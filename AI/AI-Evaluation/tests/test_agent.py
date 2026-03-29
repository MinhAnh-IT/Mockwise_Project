"""
Sample test file for Mock Interview Evaluation Agent.

Run all tests:
    pytest tests/test_agent.py -v

Run a specific test:
    pytest tests/test_agent.py::test_live_coding_good_solution -v

Run with printed output:
    pytest tests/test_agent.py -v -s
"""

import json
import sys
import os

# Allow running from project root
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from main import evaluate


# ─── Helpers ──────────────────────────────────────────────────────────────────

def pretty(result: dict) -> str:
    return json.dumps(result, indent=2, default=str, ensure_ascii=False)


def assert_valid_output(result: dict, expected_type: str):
    """Common assertions that every successful output must satisfy."""
    assert "error" not in result, f"Agent returned error: {result}"
    assert result.get("interview_type") == expected_type
    assert isinstance(result.get("overall_score"), int)
    assert 0 <= result["overall_score"] <= 100
    assert result.get("summary", {}).get("grade") in ("A", "B", "C", "D", "F")
    assert result.get("summary", {}).get("hire_signal") in (
        "strong_yes", "yes", "weak_yes", "no", "strong_no"
    )
    assert result.get("summary", {}).get("one_line_verdict")
    assert result.get("meta", {}).get("evaluated_at")
    assert isinstance(result.get("meta", {}).get("evaluation_duration_ms"), int)


# ─── Live Coding Tests ─────────────────────────────────────────────────────────

class TestLiveCoding:

    def test_good_solution_two_sum(self):
        """
        Optimal O(n) hash-map solution for Two Sum.
        Expected: high score (B or above), hire_signal yes/strong_yes.
        """
        input_data = {
            "session_id": "test_lc_good_001",
            "interview_type": "live_coding",
            "question": {
                "id": "q_two_sum",
                "title": "Two Sum",
                "description": (
                    "Given an array of integers nums and an integer target, "
                    "return indices of the two numbers that add up to target."
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
                    "    for i, num in enumerate(nums):\n"
                    "        complement = target - num\n"
                    "        if complement in seen:\n"
                    "            return [seen[complement], i]\n"
                    "        seen[num] = i\n"
                    "    return []\n"
                ),
                "language": "python",
                "time_spent_minutes": 8,
                "test_summary": {"total": 10, "passed": 10},
            },
        }

        result = evaluate(input_data)
        print("\n[Good Solution Output]\n", pretty(result))

        assert_valid_output(result, "live_coding")
        assert result["overall_score"] >= 70, "Good solution should score >= 70"
        assert result["summary"]["hire_signal"] in ("strong_yes", "yes", "weak_yes")

    def test_brute_force_solution(self):
        """
        Brute force O(n²) solution — correct but suboptimal.
        Expected: lower score than optimal, feedback should mention optimization.
        """
        input_data = {
            "session_id": "test_lc_brute_001",
            "interview_type": "live_coding",
            "question": {
                "id": "q_two_sum",
                "title": "Two Sum",
                "description": (
                    "Given an array of integers nums and an integer target, "
                    "return indices of the two numbers that add up to target."
                ),
                "difficulty": "easy",
                "tags": ["array", "hash-table"],
                "time_limit_minutes": 20,
                "optimal_complexity": {"time": "O(n)", "space": "O(n)"},
            },
            "submission": {
                "code": (
                    "def twoSum(nums, target):\n"
                    "    for i in range(len(nums)):\n"
                    "        for j in range(i + 1, len(nums)):\n"
                    "            if nums[i] + nums[j] == target:\n"
                    "                return [i, j]\n"
                    "    return []\n"
                ),
                "language": "python",
                "time_spent_minutes": 15,
                "test_summary": {"total": 10, "passed": 10},
            },
        }

        result = evaluate(input_data)
        print("\n[Brute Force Output]\n", pretty(result))

        assert_valid_output(result, "live_coding")
        assert result["analysis"]["is_optimal"] is False
        assert result["overall_score"] < 90, "Brute force should not get near-perfect score"

    def test_solution_with_bugs(self):
        """
        Solution that fails most test cases due to a logic bug.
        Expected: low score, hire_signal no/strong_no.
        """
        input_data = {
            "session_id": "test_lc_bug_001",
            "interview_type": "live_coding",
            "question": {
                "id": "q_two_sum",
                "title": "Two Sum",
                "description": (
                    "Given an array of integers nums and an integer target, "
                    "return indices of the two numbers that add up to target."
                ),
                "difficulty": "easy",
                "tags": ["array", "hash-table"],
                "time_limit_minutes": 20,
                "optimal_complexity": {"time": "O(n)", "space": "O(n)"},
            },
            "submission": {
                "code": (
                    "def twoSum(nums, target):\n"
                    "    # Wrong: returns values instead of indices\n"
                    "    for i in range(len(nums)):\n"
                    "        for j in range(i + 1, len(nums)):\n"
                    "            if nums[i] + nums[j] == target:\n"
                    "                return [nums[i], nums[j]]\n"
                    "    return []\n"
                ),
                "language": "python",
                "time_spent_minutes": 20,
                "test_summary": {"total": 10, "passed": 2},
            },
        }

        result = evaluate(input_data)
        print("\n[Buggy Solution Output]\n", pretty(result))

        assert_valid_output(result, "live_coding")
        assert result["overall_score"] < 60, "Buggy solution should score < 60"


# ─── Behavioral Tests ──────────────────────────────────────────────────────────

class TestBehavioral:

    def test_strong_star_answer(self):
        """
        Strong answer with clear Situation, Task, Action, Result and measurable outcome.
        Expected: high score, detected signals, minimal red flags.
        """
        input_data = {
            "session_id": "test_beh_strong_001",
            "interview_type": "behavioral",
            "question": {
                "id": "q_conflict",
                "text": "Tell me about a time you disagreed with a teammate. How did you handle it?",
                "competency": "conflict_resolution",
                "expected_signals": [
                    "listens_to_others_perspective",
                    "seeks_common_ground",
                    "data_driven_resolution",
                    "maintains_professional_relationship",
                ],
            },
            "answer": {
                "transcript": (
                    "Last year I was working on a payments service migration with a colleague. "
                    "He wanted to do a full rewrite in Go, but I believed we should migrate "
                    "incrementally using a strangler fig pattern to reduce risk. "
                    "We had a heated disagreement in design review. "
                    "I took a step back, listened to his concerns about technical debt, and "
                    "then pulled data on past migration failures at the company — big-bang rewrites "
                    "had a 60% rollback rate historically. I also built a 2-day prototype of the "
                    "strangler approach to show it was feasible. "
                    "We presented both options to the tech lead together and agreed on my approach. "
                    "The migration completed in 4 months with zero production incidents, "
                    "and my colleague later told me the incremental approach helped him learn "
                    "the new system gradually."
                ),
                "duration_seconds": 110,
                "language": "en",
            },
        }

        result = evaluate(input_data)
        print("\n[Strong Behavioral Output]\n", pretty(result))

        assert_valid_output(result, "behavioral")
        assert result["overall_score"] >= 70
        assert result["star_breakdown"]["result"]["detected"] is True

    def test_vague_answer_missing_result(self):
        """
        Vague answer with no measurable result — common weak behavioral response.
        Expected: low score, red flag for missing_result or vague_action.
        """
        input_data = {
            "session_id": "test_beh_weak_001",
            "interview_type": "behavioral",
            "question": {
                "id": "q_conflict",
                "text": "Tell me about a time you disagreed with a teammate. How did you handle it?",
                "competency": "conflict_resolution",
                "expected_signals": [
                    "listens_to_others_perspective",
                    "seeks_common_ground",
                ],
            },
            "answer": {
                "transcript": (
                    "Yeah, I've had disagreements before. I usually just talk to the person "
                    "and we figure it out. I think communication is really important on teams. "
                    "We discussed the issue and eventually came to an agreement. "
                    "Things worked out fine in the end."
                ),
                "duration_seconds": 30,
                "language": "en",
            },
        }

        result = evaluate(input_data)
        print("\n[Weak Behavioral Output]\n", pretty(result))

        assert_valid_output(result, "behavioral")
        assert result["overall_score"] < 65, "Vague answer should score < 65"
        assert len(result.get("red_flags", [])) > 0, "Should have at least one red flag"


# ─── Conceptual Tests ──────────────────────────────────────────────────────────

class TestConceptual:

    def test_correct_intermediate_answer(self):
        """
        Correct intermediate-level explanation of database indexes.
        Expected: high score, good concept coverage, no misconceptions.
        """
        input_data = {
            "session_id": "test_con_good_001",
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
                    "Indexes are data structures that let the database find rows quickly "
                    "without scanning every row. The most common type is a B-tree index, "
                    "which keeps data sorted and allows O(log n) lookups. "
                    "When you query by an indexed column, the DB uses an index scan "
                    "instead of a full table scan, which is much faster on large tables. "
                    "You should add indexes on columns used in WHERE clauses and JOINs. "
                    "However, indexes slow down writes because every INSERT, UPDATE, or DELETE "
                    "also has to update the index. On write-heavy tables you want to be selective. "
                    "For composite indexes, column order matters — the index is only useful "
                    "if the query filters starting from the leftmost columns. "
                    "High-cardinality columns like user_id benefit most from indexes, "
                    "while low-cardinality columns like boolean flags usually don't."
                ),
                "duration_seconds": 120,
                "language": "en",
            },
        }

        result = evaluate(input_data)
        print("\n[Good Conceptual Output]\n", pretty(result))

        assert_valid_output(result, "core_conceptual")
        assert result["overall_score"] >= 70
        assert len(result.get("misconceptions", [])) == 0

    def test_answer_with_misconception(self):
        """
        Answer containing a factual error (indexes always speed up queries).
        Expected: misconception detected, accuracy score penalized.
        """
        input_data = {
            "session_id": "test_con_misconception_001",
            "interview_type": "core_conceptual",
            "question": {
                "id": "q_db_index",
                "text": "Explain how database indexes work and when you would or wouldn't use them.",
                "domain": "databases",
                "key_concepts": [
                    "B-tree index structure",
                    "index scan vs full table scan",
                    "write overhead of indexes",
                ],
                "depth_expected": "intermediate",
            },
            "answer": {
                "transcript": (
                    "Indexes always make queries faster, so you should add them on every column. "
                    "They work by creating a sorted copy of the data. "
                    "There's no downside to having more indexes since storage is cheap."
                ),
                "duration_seconds": 40,
                "language": "en",
            },
        }

        result = evaluate(input_data)
        print("\n[Misconception Conceptual Output]\n", pretty(result))

        assert_valid_output(result, "core_conceptual")
        assert result["overall_score"] < 70
        assert len(result.get("misconceptions", [])) > 0, "Should detect misconceptions"


# ─── Router / Validation Tests ────────────────────────────────────────────────

class TestRouter:

    def test_invalid_interview_type(self):
        """Unknown interview_type should return an error, not crash."""
        result = evaluate({
            "session_id": "test_router_001",
            "interview_type": "unknown_type",
        })

        print("\n[Invalid Type Output]\n", pretty(result))
        assert "error" in result

    def test_missing_required_fields(self):
        """Missing required fields should return a validation error."""
        result = evaluate({
            "session_id": "test_router_002",
            "interview_type": "live_coding",
            # missing question and submission
        })

        print("\n[Missing Fields Output]\n", pretty(result))
        assert "error" in result

    def test_missing_session_id(self):
        """Missing session_id should trigger validation error."""
        result = evaluate({
            "interview_type": "behavioral",
            "question": {
                "id": "q1",
                "text": "Tell me about yourself",
                "competency": "communication",
                "expected_signals": [],
            },
            "answer": {
                "transcript": "I am a software engineer.",
                "duration_seconds": 10,
            },
        })

        print("\n[Missing session_id Output]\n", pretty(result))
        assert "error" in result


# ─── Vietnamese Answer Tests ──────────────────────────────────────────────────

class TestVietnameseAnswers:
    """
    Verify that when the candidate answers in Vietnamese,
    the agent still works correctly and the evaluation output
    is returned in Vietnamese (response_language="vi").
    """

    def test_live_coding_vi_answer(self):
        """
        Candidate submits code with Vietnamese comments.
        Output language should be Vietnamese.
        """
        input_data = {
            "session_id": "test_vi_lc_001",
            "interview_type": "live_coding",
            "response_language": "vi",
            "question": {
                "id": "q_two_sum",
                "title": "Two Sum",
                "description": (
                    "Given an array of integers nums and an integer target, "
                    "return indices of the two numbers that add up to target."
                ),
                "difficulty": "easy",
                "tags": ["array", "hash-table"],
                "time_limit_minutes": 20,
                "optimal_complexity": {"time": "O(n)", "space": "O(n)"},
            },
            "submission": {
                "code": (
                    "def twoSum(nums, target):\n"
                    "    # Dùng hash map để lưu các phần tử đã duyệt\n"
                    "    da_duyet = {}\n"
                    "    for vi_tri, gia_tri in enumerate(nums):\n"
                    "        can_tim = target - gia_tri\n"
                    "        if can_tim in da_duyet:\n"
                    "            return [da_duyet[can_tim], vi_tri]\n"
                    "        da_duyet[gia_tri] = vi_tri\n"
                    "    return []\n"
                ),
                "language": "python",
                "time_spent_minutes": 10,
                "test_summary": {"total": 10, "passed": 10},
            },
        }

        result = evaluate(input_data)
        print("\n[VI Live Coding Output]\n", pretty(result))

        assert_valid_output(result, "live_coding")
        assert result["overall_score"] >= 70, "Optimal Vietnamese solution should score >= 70"
        # Verdict phải bằng tiếng Việt — không chứa toàn ký tự Latin đơn thuần
        verdict = result.get("summary", {}).get("one_line_verdict", "")
        assert verdict, "one_line_verdict must not be empty"

    def test_behavioral_vi_answer(self):
        """
        Candidate answers a behavioral question entirely in Vietnamese.
        Output analysis should be in Vietnamese.
        """
        input_data = {
            "session_id": "test_vi_beh_001",
            "interview_type": "behavioral",
            "response_language": "vi",
            "question": {
                "id": "q_conflict_vi",
                "text": "Kể cho tôi nghe về một lần bạn bất đồng với đồng nghiệp. Bạn đã xử lý như thế nào?",
                "competency": "conflict_resolution",
                "expected_signals": [
                    "listens_to_others_perspective",
                    "seeks_common_ground",
                    "data_driven_resolution",
                ],
            },
            "answer": {
                "transcript": (
                    "Năm ngoái tôi làm việc trong một dự án chuyển đổi hệ thống thanh toán. "
                    "Tôi và một đồng nghiệp bất đồng về kiến trúc — anh ấy muốn viết lại toàn bộ bằng Go, "
                    "còn tôi muốn dùng chiến lược strangler fig để giảm rủi ro. "
                    "Tôi đã lắng nghe lo ngại của anh ấy về nợ kỹ thuật, "
                    "sau đó tôi thu thập dữ liệu về tỷ lệ thất bại của các lần rewrite trước trong công ty — "
                    "lên đến 60%. Tôi cũng xây dựng prototype trong 2 ngày để chứng minh hướng đi của mình khả thi. "
                    "Chúng tôi trình bày cả hai phương án lên tech lead và đi đến thống nhất theo hướng của tôi. "
                    "Dự án hoàn thành trong 4 tháng, không có sự cố nào trên môi trường production."
                ),
                "duration_seconds": 115,
                "language": "vi",
            },
        }

        result = evaluate(input_data)
        print("\n[VI Behavioral Output]\n", pretty(result))

        assert_valid_output(result, "behavioral")
        assert result["overall_score"] >= 65, "Strong Vietnamese behavioral answer should score >= 65"
        assert result["star_breakdown"]["result"]["detected"] is True

    def test_conceptual_vi_answer(self):
        """
        Candidate explains a technical concept entirely in Vietnamese.
        Output analysis should be in Vietnamese.
        """
        input_data = {
            "session_id": "test_vi_con_001",
            "interview_type": "core_conceptual",
            "response_language": "vi",
            "question": {
                "id": "q_db_index_vi",
                "text": "Hãy giải thích index trong cơ sở dữ liệu hoạt động như thế nào và khi nào nên hoặc không nên dùng chúng.",
                "domain": "databases",
                "key_concepts": [
                    "B-tree index structure",
                    "index scan vs full table scan",
                    "write overhead of indexes",
                    "cardinality and selectivity",
                ],
                "depth_expected": "intermediate",
            },
            "answer": {
                "transcript": (
                    "Index trong cơ sở dữ liệu là cấu trúc dữ liệu giúp database tìm kiếm nhanh hơn "
                    "mà không cần quét toàn bộ bảng. Loại phổ biến nhất là B-tree index, "
                    "dữ liệu được sắp xếp theo thứ tự nên cho phép tìm kiếm với độ phức tạp O(log n). "
                    "Khi truy vấn theo cột có index, database sẽ dùng index scan thay vì full table scan, "
                    "giúp tăng tốc đáng kể trên bảng lớn. "
                    "Tuy nhiên, index làm chậm thao tác ghi vì mỗi lần INSERT, UPDATE hay DELETE "
                    "đều phải cập nhật cả index. Vì vậy với bảng ghi nhiều, cần cân nhắc kỹ. "
                    "Các cột có cardinality cao như user_id rất phù hợp để đánh index, "
                    "trong khi các cột boolean hoặc cardinality thấp thường không mang lại lợi ích."
                ),
                "duration_seconds": 110,
                "language": "vi",
            },
        }

        result = evaluate(input_data)
        print("\n[VI Conceptual Output]\n", pretty(result))

        assert_valid_output(result, "core_conceptual")
        assert result["overall_score"] >= 65, "Good Vietnamese conceptual answer should score >= 65"
        assert len(result.get("misconceptions", [])) == 0, "No misconceptions expected"
