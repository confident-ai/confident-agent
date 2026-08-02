import WebSocket from "ws";

import {
  CONFIDENT_API_KEY,
  CONFIDENT_HANDLER,
  CONFIDENT_WS_BASE_URL,
  HEARTBEAT_INTERVAL_S,
  INITIAL_RECONNECT_DELAY_S,
  MAX_RECONNECT_DELAY_S,
} from "./config.js";
import { executeHandler, loadHandler, type HandlerFn } from "./handler/index.js";
import { logger } from "./logger.js";
import {
  type RelayRequest,
  type RelayResponse,
  RelayResponseType,
  ResponseMode,
  parseRelayRequest,
} from "./schemas.js";

export class RelayAgent {
  serverUrl: string;
  apiKey: string;
  handlerFn: HandlerFn | null;
  private reconnectDelay = INITIAL_RECONNECT_DELAY_S;
  private running = true;
  private ws: WebSocket | null = null;

  constructor(serverUrl: string, apiKey: string, handlerFn: HandlerFn | null = null) {
    this.serverUrl = serverUrl;
    this.apiKey = apiKey;
    this.handlerFn = handlerFn;
  }

  async start(): Promise<void> {
    while (this.running) {
      try {
        await this.connect();
      } catch (e) {
        logger.error(`connection error: ${errorMessage(e)}`);
      }
      if (!this.running) {
        break;
      }
      logger.info(`reconnecting in ${this.reconnectDelay}s`);
      await sleep(this.reconnectDelay * 1000);
      this.reconnectDelay = Math.min(this.reconnectDelay * 2, MAX_RECONNECT_DELAY_S);
    }
  }

  private connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      const ws = new WebSocket(this.serverUrl, {
        headers: { Authorization: `Bearer ${this.apiKey}` },
      });
      this.ws = ws;
      let heartbeat: NodeJS.Timeout | undefined;
      let opened = false;
      let error: Error | null = null;

      ws.on("open", () => {
        opened = true;
        logger.info(`connected to ${this.serverUrl}`);
        this.reconnectDelay = INITIAL_RECONNECT_DELAY_S;
        heartbeat = setInterval(() => ws.ping(), HEARTBEAT_INTERVAL_S * 1000);
      });

      ws.on("message", (data, isBinary) => {
        if (!isBinary) {
          void this.handleRequest(ws, data.toString()).catch((e) => {
            logger.error(`unhandled error while handling request: ${errorMessage(e)}`);
          });
        }
      });

      ws.on("error", (e) => {
        error = e;
      });

      ws.on("close", (code, reason) => {
        clearInterval(heartbeat);
        this.ws = null;
        if (!opened) {
          reject(error ?? new Error(`connection closed: code=${code}`));
          return;
        }
        logger.info(
          `WebSocket closed or errored: code=${code}, reason=${reason.toString()}` +
            (error ? `, error=${error.message}` : "")
        );
        resolve();
      });
    });
  }

  private async handleRequest(ws: WebSocket, raw: string): Promise<void> {
    let request: RelayRequest;
    try {
      request = parseRelayRequest(raw);
    } catch (e) {
      logger.error(`failed to parse relay request: ${errorMessage(e)}`);
      return;
    }

    if (this.handlerFn !== null) {
      logger.info(`[handler] running handler (request ${request.id})`);
      const relayResponse = await executeHandler(this.handlerFn, request);
      ws.send(JSON.stringify(relayResponse));
      logger.info(`[handler] ${relayResponse.statusCode} (request ${request.id})`);
      return;
    }

    try {
      const init: RequestInit = {
        method: request.method,
        headers: request.headers,
        signal: AbortSignal.timeout(request.timeout * 1000),
      };
      if (request.method.toUpperCase() !== "GET" && request.body !== null) {
        init.headers = { "Content-Type": "application/json", ...request.headers };
        init.body = JSON.stringify(request.body);
      }

      logger.info(`[relay] ${request.method} ${request.url} (request ${request.id})`);
      const response = await fetch(request.url, init);
      const isStreaming =
        request.responseMode === ResponseMode.SSE_STREAMING ||
        request.responseMode === ResponseMode.HTTP_STREAMING;

      if (isStreaming) {
        // Send response headers first
        const headerMsg: RelayResponse = {
          id: request.id,
          type: RelayResponseType.CHUNK,
          statusCode: response.status,
          headers: Object.fromEntries(response.headers),
          body: "",
        };
        ws.send(JSON.stringify(headerMsg));

        // Stream chunks as they arrive
        if (response.body) {
          const decoder = new TextDecoder("utf-8");
          for await (const rawChunk of response.body) {
            const text = decoder.decode(rawChunk, { stream: true });
            if (!text) {
              continue;
            }
            const chunkMsg: RelayResponse = {
              id: request.id,
              type: RelayResponseType.CHUNK,
              statusCode: response.status,
              headers: {},
              body: text,
            };
            ws.send(JSON.stringify(chunkMsg));
          }
        }

        // Signal stream end
        const endMsg: RelayResponse = {
          id: request.id,
          type: RelayResponseType.STREAM_END,
          statusCode: response.status,
          headers: {},
          body: "",
        };
        ws.send(JSON.stringify(endMsg));
        logger.info(
          `[relay] ${request.method} ${request.url} : ${response.status} streamed (request ${request.id})`
        );
      } else {
        const responseBody = await response.text();
        logger.info(
          `[relay] ${request.method} ${request.url} : ${response.status} (request ${request.id})`
        );

        const relayResponse: RelayResponse = {
          id: request.id,
          type: RelayResponseType.RESPONSE,
          statusCode: response.status,
          headers: Object.fromEntries(response.headers),
          body: responseBody,
        };
        ws.send(JSON.stringify(relayResponse));
      }
    } catch (e) {
      logger.error(`request ${request.id} failed: ${errorMessage(e)}`);
      const errorResponse: RelayResponse = {
        id: request.id,
        type: RelayResponseType.ERROR,
        statusCode: 502,
        headers: {},
        body: JSON.stringify({ error: errorMessage(e) }),
      };
      ws.send(JSON.stringify(errorResponse));
    }
  }

  stop(): void {
    this.running = false;
    this.ws?.close();
  }
}

export async function createAgent(handlerPath?: string): Promise<RelayAgent> {
  if (!CONFIDENT_API_KEY) {
    throw new Error("CONFIDENT_API_KEY is required");
  }
  if (!CONFIDENT_WS_BASE_URL) {
    throw new Error("CONFIDENT_WS_BASE_URL is required");
  }

  let handlerFn: HandlerFn | null = null;
  const path = handlerPath || CONFIDENT_HANDLER;
  if (path) {
    handlerFn = await loadHandler(path);
    logger.info(`handler mode: loaded ${handlerFn.name} from ${path}`);
  }

  return new RelayAgent(CONFIDENT_WS_BASE_URL, CONFIDENT_API_KEY, handlerFn);
}

function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : String(e);
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
