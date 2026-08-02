# Confident Agent (Java)

A lightweight bridge agent that allows Confident AI's evaluation server to reach internal API endpoints behind firewalls, without opening inbound ports.

## How it works

The agent connects outbound via WebSocket Secure (WSS) to Confident AI's evaluation server and waits for work. When an evaluation runs, requests are forwarded through the WebSocket tunnel to your internal endpoint and responses are relayed back.

Supports HTTP Response, HTTP Streaming and SSE Streaming response modes.

## Quick Start

### Docker Container (CLI)

```bash
docker run -d \
  -e CONFIDENT_API_KEY=<your-api-key> \
  -e CONFIDENT_WS_BASE_URL=wss://deepeval.confident-ai.com/ws/relay \
  confidentai/confident-agent
```

### Docker Compose (compose.yaml)

```yaml
services:
  confident-agent:
    image: confidentai/confident-agent
    restart: unless-stopped
    environment:
      - CONFIDENT_API_KEY=${CONFIDENT_API_KEY}
      - CONFIDENT_WS_BASE_URL=${CONFIDENT_WS_BASE_URL:-wss://deepeval.confident-ai.com/ws/relay}
```

### As a library

Maven:

```xml
<dependency>
  <groupId>ai.confident</groupId>
  <artifactId>confident-agent</artifactId>
  <version>1.0.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'ai.confident:confident-agent:1.0.0'
```

## Modes

### Forwarding mode (default)

Run the shaded jar (or Docker image). Incoming relay requests are forwarded as HTTP requests to your internal endpoint:

```bash
java -jar confident-agent-1.0.0.jar
```

### Handler mode (library)

Unlike the Python package, which loads an `@handler` function from a script file (`--handler` / `CONFIDENT_HANDLER`), Java has no script-file loading. Handler mode works as a library instead: depend on `ai.confident:confident-agent`, register exactly one handler with `Decorator.handler(...)` (the Java analog of the `@handler` decorator), then start the agent:

```java
import static ai.confident.agent.handler.Decorator.handler;

import ai.confident.agent.RelayAgent;
import ai.confident.agent.schemas.Response;

public class MyAgent {
    public static void main(String[] args) throws InterruptedException {
        handler(request -> {
            // your model / RAG pipeline here
            Response response = new Response("answer to: " + request.input);
            response.retrievalContext = java.util.List.of("chunk 1", "chunk 2");
            return response; // or simply return a String
        });

        RelayAgent.createAgent().start();
    }
}
```

The handler receives a `Request` (input, context, retrieval_context, turns, ...) and must return either a `String` or a `Response`. Success is relayed as a 200 JSON response; exceptions and timeouts are relayed as 500 errors.

## Configuration

| Environment variable | Default | Description |
| --- | --- | --- |
| `CONFIDENT_API_KEY` | (required) | API key used as the `Authorization: Bearer` credential |
| `CONFIDENT_WS_BASE_URL` | `wss://deepeval.confident-ai.com/ws/relay` | Relay WebSocket endpoint |

A `.env` file in the working directory is loaded on startup (values never override real environment variables).

## Requirements

- Java 17+
- Outbound internet access (WSS/443)
- Network access to your internal API endpoint

## Mapping from the Python package

| Python | Java |
| --- | --- |
| `confident_agent/agent.py` (`RelayAgent`, `create_agent`) | `src/main/java/ai/confident/agent/RelayAgent.java` (`RelayAgent`, static `createAgent()`) |
| `confident_agent/config.py` | `src/main/java/ai/confident/agent/Config.java` |
| `confident_agent/main.py` + `__main__.py` | `src/main/java/ai/confident/agent/Main.java` (shaded jar entry point) |
| `confident_agent/schemas.py` | `src/main/java/ai/confident/agent/schemas/` — one class per file: `ResponseMode`, `RelayRequest`, `RelayResponseType`, `RelayResponse`, `ToolCall`, `Turn`, `Request`, `Response` |
| `confident_agent/handler/decorator.py` (`@handler`) | `src/main/java/ai/confident/agent/handler/Decorator.java` (`Decorator.handler(...)`) + `HandlerFunction.java` |
| `confident_agent/handler/executor.py` (`execute_handler`) | `src/main/java/ai/confident/agent/handler/Executor.java` (`Executor.executeHandler(...)`) |
| `confident_agent/handler/loader.py` (`load_handler`, `HandlerError`) | No loader — Java cannot load handlers from script files; only `HandlerError.java` is ported. `CONFIDENT_HANDLER` and `--handler` are therefore omitted. |
| `pyproject.toml` | `pom.xml` |
