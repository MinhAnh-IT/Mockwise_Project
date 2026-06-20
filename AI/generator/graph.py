import contextvars
import logging
import time

from langgraph.graph import StateGraph, END

from generator.state import GeneratorState
from generator.nodes.generator_router import generator_router_node
from generator.nodes.problem_analyzer import problem_analyzer_node
from generator.nodes.testcase_generator import testcase_generator_node
from generator.nodes.input_generator import input_generator_node
from generator.nodes.expected_verifier import expected_verifier_node
from generator.nodes.testcase_validator import testcase_validator_node

log = logging.getLogger("generator")
log.setLevel(logging.INFO)

# Per-thread progress callback, invoked by each node's wrapper as it runs. The
# async job runner (api.py) sets this inside its worker thread so it can surface
# live step progress to a polling client; left None for plain blocking calls.
_progress_cb: "contextvars.ContextVar" = contextvars.ContextVar("generator_progress_cb", default=None)


def set_progress_callback(cb) -> None:
    """Register cb(phase, node_name, summary) for the CURRENT thread/context.
    phase is "start" | "done" | "error". Safe to pass None to clear."""
    _progress_cb.set(cb)


def _emit(phase: str, name: str, summary=None) -> None:
    cb = _progress_cb.get()
    if cb is None:
        return
    try:
        cb(phase, name, summary)
    except Exception:  # noqa: BLE001 — progress reporting must never break generation
        pass


def _summary(out) -> str:
    """One-line, human-readable summary of what a node produced — so the console
    trail shows not just WHICH step ran but WHAT it did."""
    if not isinstance(out, dict):
        return ""
    bits = []
    pa = out.get("problem_analysis")
    if pa:
        bits.append(
            f'analyzed "{pa.get("title")}" — fn={pa.get("fn")}, '
            f'difficulty={pa.get("difficulty")}, unique_answer={pa.get("unique_answer")}'
        )
    rt = out.get("raw_testcases")
    if rt is not None:
        hidden = sum(1 for tc in rt if isinstance(tc, dict) and tc.get("is_hidden"))
        bits.append(f"{len(rt)} testcases ({hidden} hidden)")
    if out.get("programmatic_warning"):
        bits.append(f"prog-inputs: {out['programmatic_warning'][:90]}")
    if out.get("needs_reference_retry"):
        bits.append(f"solutions disagreed → regenerate (try {out.get('reference_retry_count')})")
    elif out.get("verifier_warning"):
        bits.append(f"verify: {out['verifier_warning'][:90]}")
    if out.get("needs_retry"):
        bits.append(f"validation failed → retry (attempt {out.get('retry_count')})")
    if out.get("generation_error"):
        bits.append(f"error: {str(out['generation_error'])[:90]}")
    fo = out.get("final_output")
    if isinstance(fo, dict):
        bits.append(f"FAILED: {fo['error']}" if fo.get("error") else "READY ✔")
    return " | ".join(b for b in bits if b)


def _logged(name: str, fn):
    """Wrap a graph node so each step prints ▶ on entry and ✓ with elapsed time +
    a short summary on exit (✗ on error). Lets an admin `docker logs -f
    ai-service` follow exactly which step the generator is on."""
    def wrapper(state):
        _emit("start", name)
        log.info("▶ %s", name)
        t = time.time()
        try:
            out = fn(state)
        except Exception:
            log.exception("✗ %s failed after %.1fs", name, time.time() - t)
            _emit("error", name)
            raise
        summary = _summary(out)
        log.info("✓ %s (%.1fs)%s", name, time.time() - t, f" — {summary}" if summary else "")
        _emit("done", name, summary)
        return out
    wrapper.__name__ = name
    return wrapper


def _route_after_router(state: GeneratorState):
    """Skip to END immediately if router set final_output (validation error)."""
    if state.get("final_output"):
        return END
    return "problem_analyzer"


def _route_after_analyzer(state: GeneratorState):
    """Skip to END immediately if analyzer set final_output (analysis failed)."""
    if state.get("final_output"):
        return END
    return "testcase_generator"


def _route_after_verifier(state: GeneratorState):
    """Regenerate the solutions (back to analyzer) when verification found
    unverifiable cases and budget remains; otherwise proceed to validation."""
    if state.get("needs_reference_retry", False):
        return "retry_reference"
    return "validate"


def _route_after_validator(state: GeneratorState):
    """Retry testcase_generator or finish."""
    if state.get("needs_retry", False):
        return "retry"
    return "done"


def build_generator_graph():
    graph = StateGraph(GeneratorState)

    # ── Register nodes (each wrapped to log ▶ entry / ✓ exit + timing) ────────
    graph.add_node("generator_router", _logged("generator_router", generator_router_node))
    graph.add_node("problem_analyzer", _logged("problem_analyzer", problem_analyzer_node))
    graph.add_node("testcase_generator", _logged("testcase_generator", testcase_generator_node))
    graph.add_node("input_generator", _logged("input_generator", input_generator_node))
    graph.add_node("expected_verifier", _logged("expected_verifier", expected_verifier_node))
    graph.add_node("testcase_validator", _logged("testcase_validator", testcase_validator_node))

    # ── Entry point ───────────────────────────────────────────────────────────
    graph.set_entry_point("generator_router")

    # ── router → analyzer (or END on validation error) ────────────────────────
    graph.add_conditional_edges(
        "generator_router",
        _route_after_router,
        {
            "problem_analyzer": "problem_analyzer",
            END: END,
        },
    )

    # ── analyzer → generator (or END on analysis error) ───────────────────────
    graph.add_conditional_edges(
        "problem_analyzer",
        _route_after_analyzer,
        {
            "testcase_generator": "testcase_generator",
            END: END,
        },
    )

    # ── generator → input_generator → verifier → validator ────────────────────
    # input_generator (no-op unless PROGRAMMATIC_INPUTS is on) swaps the hidden
    # cases' inputs for programmatically-generated large/random ones. Then
    # expected_verifier proves each case's expectedOutput by dual-solution
    # consensus and overwrites the ones it can prove. If some case is
    # unverifiable it loops back to problem_analyzer to regenerate the solutions
    # (bounded by REFERENCE_MAX_RETRIES); otherwise it proceeds to validation.
    graph.add_edge("testcase_generator", "input_generator")
    graph.add_edge("input_generator", "expected_verifier")
    graph.add_conditional_edges(
        "expected_verifier",
        _route_after_verifier,
        {
            "retry_reference": "problem_analyzer",
            "validate": "testcase_validator",
        },
    )

    # ── validator → done or retry ─────────────────────────────────────────────
    # A retry re-runs the generator, which flows back through the verifier.
    graph.add_conditional_edges(
        "testcase_validator",
        _route_after_validator,
        {
            "done": END,
            "retry": "testcase_generator",
        },
    )

    return graph.compile()
