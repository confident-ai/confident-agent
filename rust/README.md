# Confident Agent (Rust)

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

### Cargo (forwarding mode)

```bash
cargo install confident-agent
CONFIDENT_API_KEY=<your-api-key> confident-agent
```

## Handler mode (library)

Unlike the Python package, Rust cannot load a handler from a script file at runtime, so there is no `--handler` flag or `CONFIDENT_HANDLER` variable. Instead, handler mode is library-based: add the crate to your own binary, register a handler closure, and run the agent.

```bash
cargo add confident-agent
```

```rust
use confident_agent::{handler, Request, Response};

#[tokio::main]
async fn main() {
    dotenvy::dotenv().ok();
    env_logger::init();

    handler(|request: Request| async move {
        let output = format!("you said: {}", request.input);
        Ok(Response {
            output,
            retrieval_context: Some(vec!["doc-1".to_string()]),
            ..Response::default()
        })
    });

    if let Err(e) = confident_agent::run().await {
        eprintln!("{e}");
        std::process::exit(1);
    }
}
```

Handlers can also return a plain string (or fail with `confident_agent::HandlerError`):

```rust
use confident_agent::{handler, Request};

handler(|request: Request| async move {
    if request.input.is_empty() {
        return Err("empty input".into());
    }
    Ok(request.input)
});
```

Exactly one handler may be registered per agent. When no handler is registered, the agent runs in forwarding mode.

## Requirements

- Outbound internet access (WSS/443)
- Network access to your internal API endpoint

## Mapping from the Python package

| Python | Rust | Notes |
| --- | --- | --- |
| `confident_agent/__init__.py` | `src/lib.rs` | Re-exports `handler`, `Request`, `Response`, `ToolCall`, `Turn`; also exposes `create_agent`, `run`, `RelayAgent` |
| `confident_agent/main.py` + `__main__.py` | `src/main.rs` | Binary entry point; forwarding mode only (no `--handler` flag) |
| `confident_agent/agent.py` | `src/agent.rs` | `RelayAgent`, `create_agent` |
| `confident_agent/config.py` | `src/config.rs` | `CONFIDENT_HANDLER` omitted (no file loading) |
| `confident_agent/schemas.py` | `src/schemas.rs` | serde models |
| `confident_agent/handler/__init__.py` | `src/handler/mod.rs` | |
| `confident_agent/handler/decorator.py` | `src/handler/decorator.rs` | `handler(closure)` registers into a process-global registry, the analog of `@handler` |
| `confident_agent/handler/executor.py` | `src/handler/executor.rs` | `execute_handler`, `HandlerError` |
| `confident_agent/handler/loader.py` | — | No Rust equivalent: handlers are registered in code, not loaded from files. `HandlerError` lives in `executor.rs` |
