# ─── Stage 1: builder ──────────────────────────────────────────
FROM python:3.12-slim AS builder

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --upgrade pip && \
    pip install --no-cache-dir --prefix=/install -r requirements.txt

# ─── Stage 2: runtime ──────────────────────────────────────────
FROM python:3.12-slim AS runner

WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser

COPY --from=builder /install /usr/local

# Source — both feature areas under one image.
COPY --chown=appuser:appgroup evaluator/  ./evaluator/
COPY --chown=appuser:appgroup generator/  ./generator/
COPY --chown=appuser:appgroup selector/   ./selector/
COPY --chown=appuser:appgroup messaging/  ./messaging/
COPY --chown=appuser:appgroup models/     ./models/
COPY --chown=appuser:appgroup api.py      ./api.py
COPY --chown=appuser:appgroup config.py   ./config.py
COPY --chown=appuser:appgroup llm.py      ./llm.py
COPY --chown=appuser:appgroup main.py     ./main.py

USER appuser

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8000

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=10s --start-period=20s --retries=3 \
    CMD python -c "import httpx; httpx.get('http://localhost:8000/health').raise_for_status()"

CMD ["uvicorn", "api:app", "--host", "0.0.0.0", "--port", "8000"]
