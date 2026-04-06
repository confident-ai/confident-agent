# Confident Agent

A lightweight bridge agent that allows Confident AI's evaluation server to reach internal API endpoints behind firewalls, without opening inbound ports.

## How it works

The agent connects outbound via WebSocket Secure (WSS) to Confident AI's evaluation server and waits for work. When an evaluation runs, requests are forwarded through the WebSocket tunnel to your internal endpoint and responses are relayed back.

Supports HTTP Response, HTTP Streaming and SSE Streaming response modes.

![Architecture](assets/architecture.png)

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

## Requirements

- Outbound internet access (WSS/443)
- Network access to your internal API endpoint
