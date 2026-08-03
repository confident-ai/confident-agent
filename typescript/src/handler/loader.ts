import { statSync } from "node:fs";
import { homedir } from "node:os";
import { basename, join, resolve } from "node:path";
import { pathToFileURL } from "node:url";

import { registered, type HandlerFn } from "./decorator.js";

export class HandlerError extends Error {}

export async function loadHandler(path: string): Promise<HandlerFn> {
  const expanded = path.startsWith("~") ? join(homedir(), path.slice(1)) : path;
  const file = resolve(expanded);
  if (!isFile(file)) {
    throw new HandlerError(`handler file not found: ${file}`);
  }

  registered.length = 0;

  try {
    await import(`${pathToFileURL(file).href}?t=${Date.now()}`);
  } catch (e) {
    throw new HandlerError(
      `handler file failed to import: ${e instanceof Error ? e.message : String(e)}`
    );
  }

  if (registered.length === 0) {
    throw new HandlerError(
      `no handler function found in ${basename(file)} — register exactly one ` +
        "function with handler() from confident-agent"
    );
  }
  if (registered.length > 1) {
    const names = registered.map((fn) => fn.name).join(", ");
    throw new HandlerError(
      `multiple handler functions found in ${basename(file)} (${names}) — ` +
        "exactly one is allowed per agent"
    );
  }
  return registered[0];
}

function isFile(file: string): boolean {
  try {
    return statSync(file).isFile();
  } catch {
    return false;
  }
}
