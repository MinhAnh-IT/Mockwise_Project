from langgraph.graph import StateGraph, END

from agent.state import AgentState
from agent.nodes.router import router_node
from agent.nodes.live_coding_evaluator import live_coding_evaluator_node
from agent.nodes.behavioral_evaluator import behavioral_evaluator_node
from agent.nodes.conceptual_evaluator import conceptual_evaluator_node
from agent.nodes.output_validator import output_validator_node


def route_by_type(state: AgentState):
    """
    After the router node, direct to the correct evaluator.
    If router set final_output (validation error), go straight to END.
    """
    if state.get("final_output"):
        return END
    return state["interview_type"]


def check_retry_or_done(state: AgentState):
    """
    After the output_validator, decide whether to retry or finish.
    Maps to either END (done) or the specific evaluator node for retry.
    """
    if not state.get("needs_retry", False):
        return "done"
    interview_type = state["interview_type"]
    return f"retry_{interview_type}"


def build_graph():
    graph = StateGraph(AgentState)

    # ── Register nodes ────────────────────────────────────────────────────────
    graph.add_node("router", router_node)
    graph.add_node("live_coding_evaluator", live_coding_evaluator_node)
    graph.add_node("behavioral_evaluator", behavioral_evaluator_node)
    graph.add_node("conceptual_evaluator", conceptual_evaluator_node)
    graph.add_node("output_validator", output_validator_node)

    # ── Entry point ───────────────────────────────────────────────────────────
    graph.set_entry_point("router")

    # ── Router → evaluators (or END on router failure) ────────────────────────
    graph.add_conditional_edges(
        "router",
        route_by_type,
        {
            "live_coding": "live_coding_evaluator",
            "behavioral": "behavioral_evaluator",
            "core_conceptual": "conceptual_evaluator",
            END: END,
        },
    )

    # ── Each evaluator → output_validator ────────────────────────────────────
    graph.add_edge("live_coding_evaluator", "output_validator")
    graph.add_edge("behavioral_evaluator", "output_validator")
    graph.add_edge("conceptual_evaluator", "output_validator")

    # ── output_validator → done or retry back to evaluator ───────────────────
    graph.add_conditional_edges(
        "output_validator",
        check_retry_or_done,
        {
            "done": END,
            "retry_live_coding": "live_coding_evaluator",
            "retry_behavioral": "behavioral_evaluator",
            "retry_core_conceptual": "conceptual_evaluator",
        },
    )

    return graph.compile()
