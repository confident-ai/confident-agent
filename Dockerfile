FROM python:3.12-slim AS build

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    POETRY_HOME="/opt/poetry" \
    PATH="/opt/poetry/bin:$PATH"

RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl gcc g++ build-essential libpq-dev && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

RUN curl -sSL https://install.python-poetry.org | python - && \
    poetry config virtualenvs.create false

COPY pyproject.toml poetry.lock ./

RUN poetry install --no-root --no-interaction --no-ansi

COPY . .

FROM python:3.12-slim AS runtime

WORKDIR /app

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN addgroup --system confident && adduser --system --ingroup confident confident

COPY --from=build /usr/local /usr/local
COPY --from=build /app /app
RUN chown -R confident:confident /app

USER confident

CMD ["python", "main.py"]
