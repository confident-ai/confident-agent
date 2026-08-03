# Confident Agent (Go)

A lightweight bridge agent that allows Confident AI's evaluation server to reach internal API endpoints behind firewalls, without opening inbound ports.

This is the Go port of the Python agent in [`../python`](../python). The wire protocol, behavior, and configuration are identical.

## How it works

The agent connects outbound via WebSocket Secure (WSS) to Confident AI's evaluation server and waits for work. When an evaluation runs, requests are forwarded through the WebSocket tunnel to your internal endpoint and responses are relayed back.

Supports HTTP Response, HTTP Streaming and SSE Streaming response modes.

![Architecture](../assets/architecture.png)

## Quick Start

### Docker Container (CLI)

```bash
docker run -d \
  -e CONFIDENT_API_KEY=<your-api-key> \
  -e CONFIDENT_WS_BASE_URL=wss://deepeval.confident-ai.com/ws/relay \
  confidentai/confident-agent-go
```

### Docker Compose (compose.yaml)

```yaml
services:
  confident-agent:
    image: confidentai/confident-agent-go
    restart: unless-stopped
    environment:
      - CONFIDENT_API_KEY=${CONFIDENT_API_KEY}
      - CONFIDENT_WS_BASE_URL=${CONFIDENT_WS_BASE_URL:-wss://deepeval.confident-ai.com/ws/relay}
```

### As a library (forwarding mode)

```bash
go get github.com/confident-ai/confident-agent/go
```

```go
package main

import (
	"log"

	confidentagent "github.com/confident-ai/confident-agent/go"
)

func main() {
	if err := confidentagent.Run(); err != nil {
		log.Fatal(err)
	}
}
```

## Handler mode

The Python agent loads a `@handler` function from a file given via `--handler` or `CONFIDENT_HANDLER`. Go cannot load code from a file path at runtime, so handler mode works as a library instead: import the package, register your handler with `confidentagent.Handler`, then call `confidentagent.Run()`. There is no `--handler` flag and no `CONFIDENT_HANDLER` variable; the shipped binary only runs forwarding mode.

```go
package main

import (
	"log"

	confidentagent "github.com/confident-ai/confident-agent/go"
)

func main() {
	confidentagent.Handler(func(request confidentagent.Request) (any, error) {
		return confidentagent.Response{
			Output:           "echo: " + request.Input,
			RetrievalContext: request.RetrievalContext,
		}, nil
	})

	if err := confidentagent.Run(); err != nil {
		log.Fatal(err)
	}
}
```

A handler may return a `string`, a `confidentagent.Response`, or a `*confidentagent.Response`. Exactly one handler may be registered per agent. Handler execution is bounded by the per-request timeout sent by the evaluation server.

For finer control, use `confidentagent.CreateAgent()` and drive `Start(ctx)` / `Stop()` yourself instead of `Run()`.

## File mapping

Go packages are directories, so the files under `python/confident_agent/handler/` live in the root package as separate files:

| Python | Go |
| --- | --- |
| `confident_agent/agent.py` | `agent.go` |
| `confident_agent/config.py` | `config.go` |
| `confident_agent/schemas.py` | `schemas.go` |
| `confident_agent/handler/decorator.py` | `decorator.go` |
| `confident_agent/handler/executor.py` | `executor.go` |
| `confident_agent/handler/loader.py` | — (no runtime file loading in Go; `HandlerError` lives in `executor.go`) |
| `confident_agent/main.py` + `__main__.py` | `cmd/confident-agent/main.go` |

## Configuration

- `CONFIDENT_API_KEY` (required)
- `CONFIDENT_WS_BASE_URL` (defaults to `wss://deepeval.confident-ai.com/ws/relay`)

The CLI loads a `.env` file from the working directory if present (see `.env.example`).

## Requirements

- Outbound internet access (WSS/443)
- Network access to your internal API endpoint
