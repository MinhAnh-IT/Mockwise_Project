from agent.graph import build_graph

_graph = None


def get_graph():
    global _graph
    if _graph is None:
        _graph = build_graph()
    return _graph


def evaluate(input_json: dict) -> dict:
    """
    Main entry point for the Mock Interview Evaluation Agent.

    Args:
        input_json: dict matching LiveCodingInput, BehavioralInput, or ConceptualInput schema

    Returns:
        dict matching the corresponding output schema, or error dict
    """
    graph = get_graph()
    result = graph.invoke({
        "raw_input": input_json,
        "interview_type": None,
        "validated_input": None,
        "retry_count": 0,
        "needs_retry": False,
        "evaluation_error": None,
        "last_prompt": None,
        "raw_output": None,
        "final_output": None,
        "evaluation_start_ms": None,
    })
    return result.get("final_output", {"error": "evaluation_failed", "detail": "No output produced"})


# ─── Example usage (run directly for quick smoke-test) ────────────────────────

if __name__ == "__main__":
    import json

    # ── Live Coding Example ──────────────────────────────────────────────────
    live_coding_input = {
        "session_id": "sess_lc_001",
        "interview_type": "live_coding",
        "question": {
            "id": "q_two_sum",
            "title": "Two Sum",
            "description": (
                "Given an array of integers `nums` and an integer `target`, "
                "return indices of the two numbers such that they add up to target. "
                "You may assume that each input would have exactly one solution, "
                "and you may not use the same element twice."
            ),
            "difficulty": "easy",
            "tags": ["array", "hash-table"],
            "time_limit_minutes": 20,
            "optimal_complexity": {"time": "O(n)", "space": "O(n)"},
        },
        "submission": {
            "code": (
                "def twoSum(nums, target):\n"
                "    seen = {}\n"
                "    for i, n in enumerate(nums):\n"
                "        diff = target - n\n"
                "        if diff in seen:\n"
                "            return [seen[diff], i]\n"
                "        seen[n] = i\n"
                "    return []\n"
            ),
            "language": "python",
            "time_spent_minutes": 8,
            "test_summary": {"total": 10, "passed": 10},
        },
    }

    # ── Behavioral Example ───────────────────────────────────────────────────
    behavioral_input = {
        "session_id": "sess_beh_001",
        "interview_type": "behavioral",
        "question": {
            "id": "q_conflict",
            "text": "Tell me about a time you had a disagreement with a teammate. How did you handle it?",
            "competency": "conflict_resolution",
            "expected_signals": [
                "listens_to_others_perspective",
                "seeks_common_ground",
                "maintains_professional_relationship",
                "data_driven_resolution",
            ],
        },
        "answer": {
            "transcript": (
                "Sure. Last year I was working on a payments microservice migration with a colleague. "
                "He wanted to rewrite everything in Go, but I thought we should migrate incrementally "
                "using a strangler fig pattern to reduce risk. We had a pretty heated back-and-forth "
                "in a design review. I pulled together data on past migration failures at the company "
                "and showed that big-bang rewrites had a 60% rollback rate. I also prototyped the "
                "strangler approach in two days to show it was feasible. We presented both options "
                "to the tech lead and agreed on my approach. The migration finished in 4 months with "
                "zero production incidents."
            ),
            "duration_seconds": 95,
            "language": "en",
        },
    }

    # ── Conceptual Example ───────────────────────────────────────────────────
    conceptual_input = {
        "session_id": "sess_con_001",
        "interview_type": "core_conceptual",
        "question": {
            "id": "q_db_index",
            "text": "Explain how database indexes work and when you would or wouldn't use them.",
            "domain": "databases",
            "key_concepts": [
                "B-tree index structure",
                "index scan vs full table scan",
                "write overhead of indexes",
                "cardinality and selectivity",
                "composite index column ordering",
            ],
            "depth_expected": "intermediate",
        },
        "answer": {
            "transcript": (
                "Indexes are data structures that allow the database to find rows quickly without "
                "scanning every row. The most common type is a B-tree index, which keeps data sorted "
                "and allows searches in O(log n) time. When you query by an indexed column, the DB "
                "uses an index scan instead of a full table scan, which is much faster on large tables. "
                "You should add indexes on columns used in WHERE clauses and JOIN conditions. "
                "However, indexes slow down writes because every INSERT, UPDATE, or DELETE has to "
                "also update the index. So on write-heavy tables you want to be selective. "
                "For composite indexes, the column order matters — the index can only be used "
                "efficiently if the query filters from the leftmost columns."
            ),
            "duration_seconds": 110,
            "language": "en",
        },
    }

    examples = {
        "live_coding": live_coding_input,
        "behavioral": behavioral_input,
        "core_conceptual": conceptual_input,
    }

    import sys
    example_type = sys.argv[1] if len(sys.argv) > 1 else "live_coding"
    selected = examples.get(example_type, live_coding_input)

    print(f"\nRunning evaluation for: {example_type}")
    print("=" * 60)
    output = evaluate(selected)
    print(json.dumps(output, indent=2, default=str))
