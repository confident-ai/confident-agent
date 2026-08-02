#!/usr/bin/env node
import "dotenv/config";

import { parseArgs } from "node:util";

import { createAgent } from "./agent.js";
import { logger } from "./logger.js";

const USAGE = `usage: confident-agent [-h] [--handler HANDLER]

Confident AI relay agent. Authenticates with the CONFIDENT_API_KEY environment
variable. Forwards evaluation requests to an internal endpoint, or runs a local
handler function when --handler is given.

options:
  -h, --help         show this help message and exit
  --handler HANDLER  Path to a file containing one registered handler function
                     (falls back to CONFIDENT_HANDLER). Omit for forwarding mode.`;

async function main(handlerPath?: string): Promise<void> {
  const agent = await createAgent(handlerPath);

  process.on("SIGINT", () => agent.stop());
  process.on("SIGTERM", () => agent.stop());

  logger.info("starting relay agent");
  await agent.start();
  logger.info("relay agent stopped");
}

export function run(): void {
  let values: { handler?: string; help?: boolean };
  try {
    ({ values } = parseArgs({
      options: {
        handler: { type: "string" },
        help: { type: "boolean", short: "h" },
      },
    }));
  } catch (e) {
    console.error(e instanceof Error ? e.message : String(e));
    console.error(USAGE);
    process.exit(2);
  }

  if (values.help) {
    console.log(USAGE);
    return;
  }

  main(values.handler).catch((e) => {
    logger.error(e instanceof Error ? e.message : String(e));
    process.exit(1);
  });
}

run();
