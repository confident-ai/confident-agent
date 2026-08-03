package ai.confident.agent;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ai.confident.agent.handler.Decorator;
import ai.confident.agent.handler.Executor;
import ai.confident.agent.handler.HandlerFunction;
import ai.confident.agent.schemas.RelayRequest;
import ai.confident.agent.schemas.RelayResponse;
import ai.confident.agent.schemas.RelayResponseType;
import ai.confident.agent.schemas.ResponseMode;

public class RelayAgent {

    private static final Logger logger = Logger.getLogger("relay-agent");
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> RESTRICTED_HEADERS =
            Set.of("connection", "content-length", "expect", "host", "upgrade");

    private final String serverUrl;
    private final String apiKey;
    private final HandlerFunction handlerFn;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ExecutorService requestPool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "confident-relay");
        thread.setDaemon(true);
        return thread;
    });
    private final Object sendLock = new Object();

    private volatile long reconnectDelay = Config.INITIAL_RECONNECT_DELAY_S;
    private volatile boolean running = true;
    private volatile WebSocket currentWs;
    private volatile CompletableFuture<Void> currentClosed;

    public RelayAgent(String serverUrl, String apiKey, HandlerFunction handlerFn) {
        this.serverUrl = serverUrl;
        this.apiKey = apiKey;
        this.handlerFn = handlerFn;
    }

    public void start() throws InterruptedException {
        while (running) {
            try {
                connect();
            } catch (Exception e) {
                Throwable cause = e instanceof CompletionException && e.getCause() != null
                        ? e.getCause()
                        : e;
                logger.severe("connection error: " + cause);
            }
            if (!running) {
                break;
            }
            logger.info("reconnecting in " + reconnectDelay + "s");
            TimeUnit.SECONDS.sleep(reconnectDelay);
            reconnectDelay = Math.min(reconnectDelay * 2, Config.MAX_RECONNECT_DELAY_S);
        }
    }

    private void connect() {
        CompletableFuture<Void> closed = new CompletableFuture<>();
        WebSocket ws = httpClient.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .buildAsync(URI.create(serverUrl), new Listener(closed))
                .join();
        currentWs = ws;
        currentClosed = closed;
        logger.info("connected to " + serverUrl);
        reconnectDelay = Config.INITIAL_RECONNECT_DELAY_S;

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "confident-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(
                () -> ws.sendPing(ByteBuffer.allocate(0)),
                Config.HEARTBEAT_INTERVAL_S,
                Config.HEARTBEAT_INTERVAL_S,
                TimeUnit.SECONDS);
        try {
            closed.join();
        } finally {
            heartbeat.shutdownNow();
            ws.abort();
            currentWs = null;
            currentClosed = null;
        }
    }

    private class Listener implements WebSocket.Listener {

        private final CompletableFuture<Void> closed;
        private final StringBuilder buffer = new StringBuilder();

        Listener(CompletableFuture<Void> closed) {
            this.closed = closed;
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String raw = buffer.toString();
                buffer.setLength(0);
                requestPool.submit(() -> handleRequest(ws, raw));
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            logger.info("WebSocket closed or errored: statusCode=" + statusCode + ", reason=" + reason);
            closed.complete(null);
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            closed.completeExceptionally(error);
        }
    }

    private void handleRequest(WebSocket ws, String raw) {
        RelayRequest request;
        try {
            request = mapper.readValue(raw, RelayRequest.class);
        } catch (Exception e) {
            logger.severe("failed to parse relay request: " + e);
            return;
        }

        if (handlerFn != null) {
            logger.info("[handler] running handler (request " + request.id + ")");
            RelayResponse relayResponse = Executor.executeHandler(handlerFn, request);
            sendJson(ws, relayResponse);
            logger.info("[handler] " + relayResponse.statusCode + " (request " + request.id + ")");
            return;
        }

        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(request.url))
                    .timeout(Duration.ofSeconds(request.timeout));
            for (Map.Entry<String, String> header : request.headers.entrySet()) {
                if (!RESTRICTED_HEADERS.contains(header.getKey().toLowerCase())) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.noBody();
            if (!"GET".equals(request.method.toUpperCase()) && request.body != null) {
                bodyPublisher = HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(request.body));
                if (request.headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) {
                    builder.header("Content-Type", "application/json");
                }
            }
            HttpRequest httpRequest = builder.method(request.method.toUpperCase(), bodyPublisher).build();

            logger.info("[relay] " + request.method + " " + request.url + " (request " + request.id + ")");
            boolean isStreaming = request.responseMode == ResponseMode.SSE_STREAMING
                    || request.responseMode == ResponseMode.HTTP_STREAMING;

            if (isStreaming) {
                HttpResponse<InputStream> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());

                // Send response headers first
                sendJson(ws, new RelayResponse(
                        request.id,
                        RelayResponseType.CHUNK,
                        response.statusCode(),
                        firstValueHeaders(response.headers()),
                        ""));

                // Stream chunks as they arrive
                try (InputStream stream = response.body()) {
                    byte[] chunk = new byte[8192];
                    int read;
                    while ((read = stream.read(chunk)) != -1) {
                        String text = new String(chunk, 0, read, StandardCharsets.UTF_8);
                        if (text.isEmpty()) {
                            continue;
                        }
                        sendJson(ws, new RelayResponse(
                                request.id,
                                RelayResponseType.CHUNK,
                                response.statusCode(),
                                Map.of(),
                                text));
                    }
                }

                // Signal stream end
                sendJson(ws, new RelayResponse(
                        request.id,
                        RelayResponseType.STREAM_END,
                        response.statusCode(),
                        Map.of(),
                        ""));
                logger.info("[relay] " + request.method + " " + request.url + " : "
                        + response.statusCode() + " streamed (request " + request.id + ")");
            } else {
                HttpResponse<String> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                logger.info("[relay] " + request.method + " " + request.url + " : "
                        + response.statusCode() + " (request " + request.id + ")");

                sendJson(ws, new RelayResponse(
                        request.id,
                        RelayResponseType.RESPONSE,
                        response.statusCode(),
                        firstValueHeaders(response.headers()),
                        response.body()));
            }
        } catch (Exception e) {
            logger.severe("request " + request.id + " failed: " + e);
            String message = e.getMessage() == null || e.getMessage().isEmpty()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            sendJson(ws, new RelayResponse(
                    request.id,
                    RelayResponseType.ERROR,
                    502,
                    Map.of(),
                    mapper.createObjectNode().put("error", message).toString()));
        }
    }

    private void sendJson(WebSocket ws, RelayResponse response) {
        try {
            String json = mapper.writeValueAsString(response);
            synchronized (sendLock) {
                ws.sendText(json, true).join();
            }
        } catch (JsonProcessingException e) {
            logger.severe("failed to serialize relay response: " + e);
        }
    }

    private static Map<String, String> firstValueHeaders(HttpHeaders headers) {
        Map<String, String> result = new LinkedHashMap<>();
        headers.map().forEach((key, values) -> {
            if (!values.isEmpty()) {
                result.put(key, values.get(0));
            }
        });
        return result;
    }

    public void stop() {
        running = false;
        WebSocket ws = currentWs;
        if (ws != null) {
            ws.abort();
        }
        CompletableFuture<Void> closed = currentClosed;
        if (closed != null) {
            closed.complete(null);
        }
    }

    public static RelayAgent createAgent() {
        if (Config.CONFIDENT_API_KEY.isEmpty()) {
            throw new IllegalArgumentException("CONFIDENT_API_KEY is required");
        }
        if (Config.CONFIDENT_WS_BASE_URL.isEmpty()) {
            throw new IllegalArgumentException("CONFIDENT_WS_BASE_URL is required");
        }

        HandlerFunction handlerFn = Decorator.registeredHandler();
        if (handlerFn != null) {
            logger.info("handler mode: using registered handler");
        }

        return new RelayAgent(Config.CONFIDENT_WS_BASE_URL, Config.CONFIDENT_API_KEY, handlerFn);
    }
}
