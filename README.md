# Confident Agent

A lightweight agent that connects your private network to Confident AI's evaluation server — without opening inbound ports. It runs in one of two modes:

- **Forwarding mode** (default): requests are forwarded through the agent's outbound WebSocket tunnel to an internal API endpoint, and responses are relayed back.
- **Handler mode**: for AI apps with **no HTTP endpoint**. You write a small `@handler` function; the agent runs it locally for each evaluation input and relays the output back.

## How it works

The agent connects outbound via WebSocket Secure (WSS) to Confident AI's evaluation server and waits for work. When an evaluation runs, each request is either forwarded to your internal endpoint (forwarding mode) or passed to your handler function (handler mode).

Forwarding mode supports HTTP Response, HTTP Streaming and SSE Streaming response modes.

![Architecture](assets/architecture.png)

## Getting started

The agent is implemented in five languages, each in its own folder with the same structure and behavior. See each README for setup, configuration, and usage:

| Language   | Folder                       | Package                                                                |
| ---------- | ---------------------------- | ---------------------------------------------------------------------- |
| Python     | [`python/`](python/)         | [`confident-agent`](https://pypi.org/project/confident-agent/) on PyPI |
| TypeScript | [`typescript/`](typescript/) | `confident-agent` on npm                                               |
| Go         | [`go/`](go/)                 | `github.com/confident-ai/confident-agent/go`                           |
| Java       | [`java/`](java/)             | `ai.confident:confident-agent` on Maven Central                        |
| Rust       | [`rust/`](rust/)             | `confident-agent` on crates.io                                         |

Python and TypeScript support loading a handler from a file (`--handler`); in Go, Java, and Rust, handler mode is library-based — you register your handler in code and start the agent from your own entry point.
