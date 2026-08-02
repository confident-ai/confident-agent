import type { Request, Response } from "../schemas.js";

export type HandlerFn = (
  request: Request
) => Response | string | Promise<Response | string>;

export const registered: HandlerFn[] = [];

export function handler(fn: HandlerFn): HandlerFn {
  registered.push(fn);
  return fn;
}
