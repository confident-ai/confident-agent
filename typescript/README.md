# Confident Agent

A lightweight bridge agent that allows Confident AI's evaluation server to reach internal API endpoints behind firewalls, without opening inbound ports.

## How it works

The agent connects outbound via WebSocket Secure (WSS) to Confident AI's evaluation server and waits for work. When an evaluation runs, requests are forwarded through the WebSocket tunnel to your internal endpoint and responses are relayed back.

Supports HTTP Response, HTTP Streaming and SSE Streaming response modes.

![Architecture](assets/architecture.png)

## Quick Start

### npm (CLI)

```bash
npm install -g @confident-ai/agent

CONFIDENT_API_KEY=<your-api-key> confident-agent
```

Or run a local handler function instead of forwarding:

```ts
// my-handler.ts
import { handler, type Request } from "@confident-ai/agent";

handler(async (request: Request) => {
  return {
    output: `you said: ${request.input}`,
    retrievalContext: ["..."],
  };
});
```

```bash
CONFIDENT_API_KEY=<your-api-key> confident-agent --handler ./my-handler.ts
```

### Docker Container (CLI)

```bash
docker run -d \
  -e CONFIDENT_API_KEY=<your-api-key> \
  -e CONFIDENT_WS_BASE_URL=wss://deepeval.confident-ai.com/ws/relay \
  confidentai/confident-agent-node
```

### Docker Compose (compose.yaml)

```yaml
services:
  confident-agent:
    image: confidentai/confident-agent-node
    restart: unless-stopped
    environment:
      - CONFIDENT_API_KEY=${CONFIDENT_API_KEY}
      - CONFIDENT_WS_BASE_URL=${CONFIDENT_WS_BASE_URL:-wss://deepeval.confident-ai.com/ws/relay}
```

## Requirements

- Node.js >= 20 (>= 22.18 to load `.ts` handler files directly)
- Outbound internet access (WSS/443)
- Network access to your internal API endpoint
