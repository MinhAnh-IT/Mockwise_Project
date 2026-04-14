from fastapi import FastAPI, HTTPException, Query, Security
from fastapi.responses import JSONResponse
from fastapi.security.api_key import APIKeyHeader
import os
import time
from main import evaluate, get_graph, generate_testcases, get_generator_graph
from config import GOOGLE_API_KEY, MODEL_NAME, MODEL_VERSION
from generator.leetcode_fetcher import (
    fetch_leetcode_problem,
    LeetCodeNotFoundError,
    LeetCodePremiumError,
    LeetCodeFetchError,
)

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

    try:
        get_generator_graph()
        checks["generator_graph"] = "ok"
    except Exception as e:
        checks["generator_graph"] = f"error: {e}"
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


@app.post("/generate-testcases")
def generate_testcases_endpoint(payload: dict, _: None = Security(_require_api_key)):
    try:
        result = generate_testcases(payload)
        if "error" in result:
            raise HTTPException(status_code=422, detail=result)
        return result
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/leetcode/fetch")
def leetcode_fetch(
    url: str = Query(..., description="LeetCode problem URL or bare slug"),
    session_cookie: str | None = Query(
        None, description="Optional LEETCODE_SESSION cookie for premium problems"
    ),
):
    """Fetch a LeetCode problem's structured data from its public GraphQL endpoint.

    Example:
        GET /leetcode/fetch?url=https://leetcode.com/problems/two-sum/
        GET /leetcode/fetch?url=two-sum
    """
    try:
        problem = fetch_leetcode_problem(url, session_cookie=session_cookie)
        return problem.model_dump()
    except LeetCodeNotFoundError as e:
        raise HTTPException(status_code=404, detail=str(e))
    except LeetCodePremiumError as e:
        raise HTTPException(status_code=403, detail=str(e))
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except LeetCodeFetchError as e:
        raise HTTPException(status_code=502, detail=str(e))
