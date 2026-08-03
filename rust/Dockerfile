FROM rust:1-slim-trixie AS build

WORKDIR /app

COPY . .

RUN cargo build --release --locked

FROM debian:trixie-slim AS runtime

WORKDIR /app

RUN addgroup --system confident && adduser --system --ingroup confident confident

COPY --from=build /app/target/release/confident-agent /usr/local/bin/confident-agent
RUN chown -R confident:confident /app

USER confident

CMD ["confident-agent"]
