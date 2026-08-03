.PHONY: setup dev build run start

setup:
	poetry install

dev:
	poetry run confident-agent

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

