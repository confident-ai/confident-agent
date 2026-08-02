# Contributing to Confident Agent

## Prerequisites

- Python 3.12+
- [Poetry](https://python-poetry.org/docs/#installation)
- Docker (for container builds)

## Setup

1. Clone the repository and navigate to the Python folder:

```bash
cd confident-agent/python
```

2. Install dependencies:

```bash
make setup
```

3. Create a `.env` file with your configuration:

```bash
CONFIDENT_API_KEY=<your-api-key>
CONFIDENT_WS_BASE_URL=wss://deepeval.confident-ai.com/ws/relay
```

4. Run the agent locally:

```bash
make dev
```

## Available Make Commands

| Command      | Description                                   |
| ------------ | --------------------------------------------- |
| `make setup` | Install dependencies via Poetry               |
| `make dev`   | Run the agent locally                         |
| `make build` | Build multi-platform Docker image             |
| `make run`   | Run the Docker image with `.env` file          |
| `make start` | Start the agent via Docker Compose (detached) |

## Contributing Guide

1. Create a new branch from `main` for each change you intend to make.
2. Before submitting a pull request, verify that the agent runs without errors.
3. Combine all your commits into a single commit before requesting a review (use interactive rebase or equivalent).
4. Use the following commit message structure:
   - `feat: <feature description>` — for new features
   - `fix: <bug description>` — for bug fixes
   - `refac: <refactor description>` — for code refactoring
   - `chore: <other changes>` — for chores (CI, docs, dependencies, etc.)
5. Keep your pull request focused on a single topic or change.
6. Open your pull request against the `main` branch.
