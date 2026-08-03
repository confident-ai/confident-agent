.PHONY: setup dev build run start

setup:
	cargo fetch

dev:
	cargo run

build:
	docker buildx build \
		--platform linux/amd64,linux/arm64 \
		-t confidentai/confident-agent \
		-f Dockerfile \
		.

run:
	docker run --rm --env-file .env confidentai/confident-agent

start:
	docker compose up --build -d
