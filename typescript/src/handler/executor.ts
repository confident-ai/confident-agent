import { logger } from "../logger.js";
import {
  type RelayRequest,
  type RelayResponse,
  RelayResponseType,
  type Response,
  parseRequest,
  serializeResponse,
} from "../schemas.js";
import type { HandlerFn } from "./decorator.js";

class TimeoutError extends Error {}

export async function executeHandler(
  fn: HandlerFn,
  request: RelayRequest
): Promise<RelayResponse> {
  try {
    const body =
      typeof request.body === "object" && request.body !== null && !Array.isArray(request.body)
        ? (request.body as Record<string, unknown>)
        : {};
    const handlerRequest = parseRequest(body);

    const result = await withTimeout(
      Promise.resolve().then(() => fn(handlerRequest)),
      request.timeout
    );

    const response = normalize(result);
    return {
      id: request.id,
      type: RelayResponseType.RESPONSE,
      statusCode: 200,
      headers: { "Content-Type": "application/json" },
      body: serializeResponse(response),
    };
  } catch (e) {
    if (e instanceof TimeoutError) {
      logger.error(
        `handler timed out after ${request.timeout}s (request ${request.id})`
      );
      return errorResponse(
        request.id,
        `handler timed out after ${request.timeout}s`
      );
    }
    const message = e instanceof Error ? e.message : String(e);
    logger.error(`handler raised for request ${request.id}: ${message}`);
    return errorResponse(request.id, message || typeName(e));
  }
}

async function withTimeout<T>(promise: Promise<T>, seconds: number): Promise<T> {
  let timer: NodeJS.Timeout | undefined;
  const timeout = new Promise<never>((_, reject) => {
    timer = setTimeout(() => reject(new TimeoutError()), seconds * 1000);
  });
  try {
    return await Promise.race([promise, timeout]);
  } finally {
    clearTimeout(timer);
  }
}

function normalize(result: unknown): Response {
  if (isResponse(result)) {
    return result;
  }
  if (typeof result === "string") {
    return { output: result };
  }
  if (result === null || result === undefined) {
    throw new Error("handler returned null — return a string or a Response");
  }
  throw new Error(
    `handler returned ${typeName(result)} — return a string or a Response`
  );
}

function isResponse(result: unknown): result is Response {
  return (
    typeof result === "object" &&
    result !== null &&
    typeof (result as Response).output === "string"
  );
}

function typeName(value: unknown): string {
  if (typeof value === "object" && value !== null) {
    return value.constructor?.name ?? "object";
  }
  return typeof value;
}

function errorResponse(requestId: string, message: string): RelayResponse {
  return {
    id: requestId,
    type: RelayResponseType.ERROR,
    statusCode: 500,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ error: message }),
  };
}
