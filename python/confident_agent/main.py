from dotenv import load_dotenv
load_dotenv()

import argparse
import asyncio
import logging
import signal

from .agent import create_agent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("relay-agent")


async def main(handler_path=None):
    agent = create_agent(handler_path=handler_path)

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, agent.stop)

    logger.info("starting relay agent")
    await agent.start()
    logger.info("relay agent stopped")


def run():
    parser = argparse.ArgumentParser(
        prog="confident-agent",
        description=(
            "Confident AI relay agent. Authenticates with the CONFIDENT_API_KEY "
            "environment variable. Forwards evaluation requests to an internal "
            "endpoint, or runs a local @handler function when --handler is given."
        ),
    )
    parser.add_argument(
        "--handler",
        default=None,
        help=(
            "Path to a Python file containing one @handler function "
            "(falls back to CONFIDENT_HANDLER). Omit for forwarding mode."
        ),
    )
    args = parser.parse_args()
    asyncio.run(main(handler_path=args.handler))


if __name__ == "__main__":
    run()
