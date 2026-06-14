from langgraph.graph import StateGraph, END

from generator.state import GeneratorState
from generator.nodes.generator_router import generator_router_node
from generator.nodes.problem_analyzer import problem_analyzer_node
from generator.nodes.testcase_generator import testcase_generator_node
from generator.nodes.expected_verifier import expected_verifier_node
from generator.nodes.testcase_validator import testcase_validator_node


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

    # ── Register nodes ────────────────────────────────────────────────────────
    graph.add_node("generator_router", generator_router_node)
    graph.add_node("problem_analyzer", problem_analyzer_node)
    graph.add_node("testcase_generator", testcase_generator_node)
    graph.add_node("expected_verifier", expected_verifier_node)
    graph.add_node("testcase_validator", testcase_validator_node)

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

    # ── generator → verifier → validator ──────────────────────────────────────
    # expected_verifier proves each case's expectedOutput by dual-solution
    # consensus and overwrites the ones it can prove. If some case is
    # unverifiable it loops back to problem_analyzer to regenerate the solutions
    # (bounded by REFERENCE_MAX_RETRIES); otherwise it proceeds to validation.
    graph.add_edge("testcase_generator", "expected_verifier")
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
