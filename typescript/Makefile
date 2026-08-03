.PHONY: setup dev build run start

setup:
	npm install

dev:
	npm run build && node dist/main.js

build:
	docker buildx build \
		--platform linux/amd64,linux/arm64 \
		-t confidentai/confident-agent-node \
		-f Dockerfile \
		.

run:
	docker run --rm --env-file .env confidentai/confident-agent-node

start:
	docker compose up --build -d
