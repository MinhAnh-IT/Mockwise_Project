"""Cross-question overall reviewer LangGraph.

Pipeline: input_validator → overall_evaluator → output_validator → END.
The output_validator can request a retry back into overall_evaluator
(bounded by config.MAX_RETRIES) if the LLM produced no parsable output.
"""
from __future__ import annotations

from langgraph.graph import StateGraph, END

from overall_reviewer.nodes.input_validator import input_validator_node
from overall_reviewer.nodes.output_validator import output_validator_node
from overall_reviewer.nodes.overall_evaluator import overall_evaluator_node
from overall_reviewer.state import OverallReviewState


def _route_after_input(state: OverallReviewState):
    # input_validator sets final_output on parse failure — short-circuit.
    return END if state.get("final_output") else "overall_evaluator"


def _route_after_validator(state: OverallReviewState):
    return "retry_overall_evaluator" if state.get("needs_retry", False) else END


def build_graph():
    g = StateGraph(OverallReviewState)
    g.add_node("input_validator", input_validator_node)
    g.add_node("overall_evaluator", overall_evaluator_node)
    g.add_node("output_validator", output_validator_node)

    g.set_entry_point("input_validator")
    g.add_conditional_edges(
        "input_validator",
        _route_after_input,
        {"overall_evaluator": "overall_evaluator", END: END},
    )
    g.add_edge("overall_evaluator", "output_validator")
    g.add_conditional_edges(
        "output_validator",
        _route_after_validator,
        {"retry_overall_evaluator": "overall_evaluator", END: END},
    )
    return g.compile()


# Singleton — the compiled graph is stateless and reusable across requests.
_graph_instance = None


def get_overall_reviewer_graph():
    global _graph_instance
    if _graph_instance is None:
        _graph_instance = build_graph()
    return _graph_instance


def review_session(input_json: dict) -> dict:
    """Synchronous entry point — runs the graph and returns the final_output
    dict (suitable for direct serialisation onto Kafka)."""
    graph = get_overall_reviewer_graph()
    initial: OverallReviewState = {
        "raw_input": input_json,
        "validated_input": None,
        "retry_count": 0,
        "needs_retry": False,
        "evaluation_error": None,
        "last_prompt": None,
        "raw_output": None,
        "final_output": None,
        "evaluation_start_ms": None,
    }
    result = graph.invoke(initial)
    return result.get("final_output") or {
        "session_id": input_json.get("sessionId") or input_json.get("session_id"),
        "error": "overall_review_no_output",
    }
