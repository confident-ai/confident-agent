package ai.confident.agent.handler;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import ai.confident.agent.schemas.RelayRequest;
import ai.confident.agent.schemas.RelayResponse;
import ai.confident.agent.schemas.RelayResponseType;
import ai.confident.agent.schemas.Request;
import ai.confident.agent.schemas.Response;

public final class Executor {

    private static final Logger logger = Logger.getLogger("relay-agent");
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "confident-handler");
        thread.setDaemon(true);
        return thread;
    });

    private Executor() {
    }

    public static RelayResponse executeHandler(HandlerFunction fn, RelayRequest request) {
        try {
            Request handlerRequest = request.body instanceof Map
                    ? mapper.convertValue(request.body, Request.class)
                    : new Request();

            Future<Object> future = pool.submit(() -> fn.apply(handlerRequest));
            Object result;
            try {
                result = future.get(request.timeout, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw e;
            }

            Response response = normalize(result);
            return new RelayResponse(
                    request.id,
                    RelayResponseType.RESPONSE,
                    200,
                    Map.of("Content-Type", "application/json"),
                    mapper.writeValueAsString(response));
        } catch (TimeoutException e) {
            logger.severe(
                    "handler timed out after " + request.timeout + "s (request " + request.id + ")");
            return errorResponse(request.id, "handler timed out after " + request.timeout + "s");
        } catch (Exception e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null
                    ? e.getCause()
                    : e;
            String message = cause.getMessage() == null || cause.getMessage().isEmpty()
                    ? cause.getClass().getSimpleName()
                    : cause.getMessage();
            logger.severe("handler raised for request " + request.id + ": " + message);
            return errorResponse(request.id, message);
        }
    }

    private static Response normalize(Object result) {
        if (result instanceof Response response) {
            return response;
        }
        if (result instanceof String output) {
            return new Response(output);
        }
        if (result == null) {
            throw new IllegalArgumentException(
                    "handler returned null — return a string or a Response");
        }
        throw new IllegalArgumentException(
                "handler returned "
                        + result.getClass().getSimpleName()
                        + " — return a string or a Response");
    }

    private static RelayResponse errorResponse(String requestId, String message) {
        return new RelayResponse(
                requestId,
                RelayResponseType.ERROR,
                500,
                Map.of("Content-Type", "application/json"),
                mapper.createObjectNode().put("error", message).toString());
    }
}
