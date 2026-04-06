from dotenv import load_dotenv
load_dotenv()

import asyncio
import logging
import signal

from agent import create_agent

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger("relay-agent")


async def main():
    agent = create_agent()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        loop.add_signal_handler(sig, agent.stop)

    logger.info("starting relay agent")
    await agent.start()
    logger.info("relay agent stopped")


if __name__ == "__main__":
    asyncio.run(main())
