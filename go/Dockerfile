FROM golang:1.26 AS build

WORKDIR /app

ENV CGO_ENABLED=0

COPY go.mod go.sum ./

RUN go mod download

COPY . .

RUN go build -o /app/bin/confident-agent ./cmd/confident-agent

FROM debian:12-slim AS runtime

WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN addgroup --system confident && adduser --system --ingroup confident confident

COPY --from=build /app/bin/confident-agent /app/confident-agent
RUN chown -R confident:confident /app

USER confident

CMD ["/app/confident-agent"]
