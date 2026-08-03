.PHONY: setup dev build run start

setup:
	mvn -q dependency:go-offline

dev:
	mvn -q package && java -jar target/confident-agent-*-all.jar

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
