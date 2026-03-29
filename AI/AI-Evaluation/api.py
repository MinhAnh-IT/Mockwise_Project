from fastapi import FastAPI, HTTPException, Security
from fastapi.responses import JSONResponse
from fastapi.security.api_key import APIKeyHeader
import os
import time
from main import evaluate, get_graph
from config import GOOGLE_API_KEY, MODEL_NAME, MODEL_VERSION

app = FastAPI(title="AI-Evaluation Service", version="1.0.0")

_API_KEY = os.getenv("SERVICE_API_KEY", "")
_api_key_header = APIKeyHeader(name="X-API-Key", auto_error=False)


def _require_api_key(api_key: str = Security(_api_key_header)) -> None:
    if _API_KEY and api_key != _API_KEY:
        raise HTTPException(status_code=401, detail="Invalid or missing API key")

_start_time = time.time()


@app.get("/health")
def health():
    checks = {}
    overall = "healthy"

    # 1. Check API key is configured
    if GOOGLE_API_KEY:
        checks["google_api_key"] = "configured"
    else:
        checks["google_api_key"] = "missing"
        overall = "unhealthy"

    # 2. Check agent graph can be loaded
    try:
        get_graph()
        checks["agent_graph"] = "ok"
    except Exception as e:
        checks["agent_graph"] = f"error: {e}"
        overall = "unhealthy"

    body = {
        "status": overall,
        "uptime_seconds": round(time.time() - _start_time, 1),
        "model": MODEL_NAME,
        "version": MODEL_VERSION,
        "checks": checks,
    }

    status_code = 200 if overall == "healthy" else 503
    return JSONResponse(content=body, status_code=status_code)


@app.post("/evaluate")
def evaluate_interview(payload: dict, _: None = Security(_require_api_key)):
    try:
        result = evaluate(payload)
        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
