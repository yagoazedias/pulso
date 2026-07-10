FROM python:3.12-slim AS builder
WORKDIR /app
RUN pip install --no-cache-dir uv
COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev --no-install-project

FROM python:3.12-slim
WORKDIR /app
COPY --from=builder /app/.venv /app/.venv
COPY pulso ./pulso
COPY migrations ./migrations
ENV PATH="/app/.venv/bin:$PATH"
ENTRYPOINT ["python", "-m", "pulso.cli"]
