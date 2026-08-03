.PHONY: setup dev build run start

setup:
	go mod download

dev:
	go run ./cmd/confident-agent

build:
	docker buildx build \
		--platform linux/amd64,linux/arm64 \
		-t confidentai/confident-agent-go \
		-f Dockerfile \
		.

run:
	docker run --rm --env-file .env confidentai/confident-agent-go

start:
	docker compose up --build -d
