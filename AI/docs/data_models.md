# Mock Interview System — Data Models

> Tài liệu định nghĩa Input/Output schema cho Evaluation Agent theo từng loại phỏng vấn.

---

## Mục lục

1. [Common Types](#common-types)
2. [Live Coding](#1-live-coding)
3. [Behavioral](#2-behavioral)
4. [Core — Conceptual](#3-core--conceptual)
5. [Common Output Fields](#4-common-output-fields)

---

## Common Types

Các enum và type dùng chung trên toàn hệ thống.

```
CandidateLevel   = "junior" | "mid" | "senior" | "staff"
Difficulty       = "easy" | "medium" | "hard"
Grade            = "A" | "B" | "C" | "D" | "F"
HireSignal       = "strong_yes" | "yes" | "weak_yes" | "no" | "strong_no"
Quality          = "excellent" | "good" | "acceptable" | "weak" | "missing"
Language         = "en" | "vi"
ResponseLanguage = "en" | "vi"           -- ngôn ngữ của toàn bộ text fields trong output (default: "vi")
ProgrammingLang  = "python" | "javascript" | "typescript" | "java" | "go" | "cpp" | "rust" | ...
Role             = "backend" | "frontend" | "fullstack" | "data" | "devops" | "mobile"
InterviewType    = "live_coding" | "behavioral" | "core_conceptual"
```

---

## 1. Live Coding

> **Flow:** Test runner chạy và xác nhận pass trước → mới gửi xuống AI Evaluation Service.
> AI không cần chấm correctness — chỉ tập trung phân tích chất lượng code và tư duy.

### Input

```json
{
  "session_id": "string",
  "interview_type": "live_coding",
  "question": {
    "id": "string",
    "title": "string",
    "description": "string",
    "difficulty": "easy | medium | hard",
    "tags": ["array", "dp", "graph", "string", "tree", "..."],
    "time_limit_minutes": 30,
    "optimal_complexity": {
      "time": "O(n)",
      "space": "O(1)"
    }
  },
  "submission": {
    "code": "string",
    "language": "python | javascript | java | ...",
    "time_spent_minutes": 24,
    "test_summary": {
      "total": 10,
      "passed": 10
    }
  },
  "response_language": "en | vi"
}
```

> `optimal_complexity` là hint từ hệ thống để AI so sánh với solution của ứng viên.
> `test_summary` cho AI biết context (ví dụ: pass 10/10 trong 24 phút).
> `response_language` quy định ngôn ngữ của toàn bộ text fields trong output (`"en"` mặc định).

### Output

```json
{
  "session_id": "string",
  "interview_type": "live_coding",
  "overall_score": 78,
  "scores": {
    "time_complexity": {
      "score": 80,
      "max": 100,
      "weight": 0.30,
      "note": "O(n log n) thay vì optimal O(n) — có thể tối ưu bằng HashMap"
    },
    "space_complexity": {
      "score": 70,
      "max": 100,
      "weight": 0.15,
      "note": "O(n) extra space, có thể tối ưu xuống O(1)"
    },
    "code_quality": {
      "score": 75,
      "max": 100,
      "weight": 0.35,
      "note": "Readable nhưng naming không nhất quán, có magic number"
    },
    "problem_solving": {
      "score": 65,
      "max": 100,
      "weight": 0.20,
      "note": "Không clarify constraints trước khi code, thiếu thói quen nêu approach"
    }
  },
  "analysis": {
    "detected_complexity": {
      "time": "O(n log n)",
      "space": "O(n)"
    },
    "optimal_complexity": {
      "time": "O(n)",
      "space": "O(1)"
    },
    "is_optimal": false,
    "code_issues": [
      {
        "type": "naming",
        "line": 5,
        "detail": "Biến 'a' không rõ nghĩa, nên đổi thành 'left_pointer'"
      },
      {
        "type": "style",
        "line": 12,
        "detail": "Magic number 999999 nên đặt thành hằng số INT_MAX"
      },
      {
        "type": "approach",
        "line": null,
        "detail": "Sort array trước khi duyệt là thừa — sliding window không cần sort"
      }
    ]
  },
  "feedback": {
    "strengths": [
      "Approach đúng hướng sliding window, logic không bị sai",
      "Code có structure rõ ràng, dễ đọc"
    ],
    "improvements": [
      "Nên nêu approach và clarify constraints trước khi code (null input? negative numbers?)",
      "Có thể bỏ bước sort để đạt O(n) thay vì O(n log n)",
      "Đặt tên biến mô tả hơn: 'a', 'b' → 'left', 'right'"
    ],
    "optimization_hint": "Dùng HashMap để track frequency thay vì sort — giảm từ O(n log n) xuống O(n)",
    "sample_optimal_solution": "string | null"
  },
  "meta": {
    "evaluated_at": "2024-01-15T10:30:00Z",
    "model_version": "evaluator-v1.0",
    "evaluation_duration_ms": 850
  },
  "summary": {
    "grade": "B",
    "hire_signal": "weak_yes",
    "one_line_verdict": "Code đúng và clean, cần cải thiện tối ưu complexity và thói quen clarify trước khi code"
  }
}
```

---

## 2. Behavioral

### Input

```json
{
  "session_id": "string",
  "interview_type": "behavioral",
  "question": {
    "id": "string",
    "text": "Tell me about a time you disagreed with your manager.",
    "competency": "conflict_resolution | leadership | ownership | teamwork | failure | growth | communication | prioritization",
    "expected_signals": [
      "shows autonomy",
      "respectful disagreement",
      "data-driven approach"
    ]
  },
  "answer": {
    "transcript": "string",
    "duration_seconds": 180,
    "language": "en | vi"
  },
  "response_language": "en | vi"
}
```

> `answer.language` là ngôn ngữ ứng viên dùng khi trả lời.
> `response_language` là ngôn ngữ AI dùng để viết feedback/output (`"vi"` mặc định).

### Output

```json
{
  "session_id": "string",
  "interview_type": "behavioral",
  "overall_score": 72,
  "scores": {
    "star_structure": {
      "score": 80,
      "max": 100,
      "weight": 0.25,
      "note": "Có đủ S/T/A nhưng thiếu Result"
    },
    "relevance": {
      "score": 90,
      "max": 100,
      "weight": 0.20,
      "note": "Câu trả lời đúng trọng tâm câu hỏi"
    },
    "specificity": {
      "score": 60,
      "max": 100,
      "weight": 0.20,
      "note": "Action còn chung chung, thiếu chi tiết cụ thể"
    },
    "impact_result": {
      "score": 55,
      "max": 100,
      "weight": 0.20,
      "note": "Không nêu kết quả đo lường được"
    },
    "self_awareness": {
      "score": 75,
      "max": 100,
      "weight": 0.15,
      "note": "Có nhận ra bài học nhưng chưa sâu"
    }
  },
  "star_breakdown": {
    "situation": {
      "detected": true,
      "quality": "good",
      "excerpt": "Khi team quyết định dùng REST thay vì GraphQL cho API mới..."
    },
    "task": {
      "detected": true,
      "quality": "good",
      "excerpt": "Tôi cần thuyết phục manager xem xét lại quyết định..."
    },
    "action": {
      "detected": true,
      "quality": "weak",
      "excerpt": "Tôi đã nói chuyện với manager về vấn đề này..."
    },
    "result": {
      "detected": false,
      "quality": "missing",
      "excerpt": null
    }
  },
  "signal_coverage": {
    "shows autonomy": {
      "detected": true,
      "evidence": "Chủ động đề xuất benchmark comparison"
    },
    "respectful disagreement": {
      "detected": true,
      "evidence": "Không phàn nàn, tìm cách đối thoại trực tiếp"
    },
    "data-driven approach": {
      "detected": false,
      "evidence": null
    }
  },
  "red_flags": [
    {
      "type": "vague_action",
      "severity": "medium",
      "detail": "Action quá chung chung ('tôi đã nói chuyện'), không rõ chuẩn bị gì, gặp ai, dùng cách tiếp cận nào"
    },
    {
      "type": "missing_result",
      "severity": "high",
      "detail": "Hoàn toàn không đề cập kết quả — quyết định cuối là gì, impact ra sao"
    }
  ],
  "feedback": {
    "strengths": [
      "Situation và Task rõ ràng, có context cụ thể",
      "Thể hiện được tinh thần chủ động và tôn trọng manager"
    ],
    "improvements": [
      "Bổ sung Result: quyết định cuối cùng là gì? Có được chấp nhận không? Impact thế nào?",
      "Chi tiết hơn ở Action: chuẩn bị dữ liệu/benchmark gì, trình bày với ai, timeline"
    ],
    "sample_stronger_answer_structure": "Situation (context rõ) → Task (mục tiêu của bạn) → Action (chuẩn bị benchmark data → present 1-1 với manager → đề xuất pilot test) → Result (team chuyển sang hybrid approach, giảm 30% query time sau 2 sprint)"
  },
  "meta": {
    "evaluated_at": "2024-01-15T10:30:00Z",
    "model_version": "evaluator-v1.0",
    "evaluation_duration_ms": 1100
  },
  "summary": {
    "grade": "C",
    "hire_signal": "weak_yes",
    "one_line_verdict": "Nền tảng tốt nhưng cần luyện nêu kết quả cụ thể và đo lường được theo STAR"
  }
}
```

---

## 3. Core — Conceptual

### Input

```json
{
  "session_id": "string",
  "interview_type": "core_conceptual",
  "question": {
    "id": "string",
    "text": "Explain the difference between process and thread. When would you use one over the other?",
    "domain": "os | networking | database | system_design | language_specific | framework | security",
    "key_concepts": [
      "memory isolation",
      "context switching",
      "GIL",
      "concurrency vs parallelism"
    ],
    "depth_expected": "trade-offs, use cases thực tế"
  },
  "answer": {
    "transcript": "string",
    "duration_seconds": 120,
    "language": "en | vi"
  },
  "response_language": "en | vi"
}
```

> `answer.language` là ngôn ngữ ứng viên dùng khi trả lời.
> `response_language` là ngôn ngữ AI dùng để viết feedback/output (`"vi"` mặc định).

### Output

```json
{
  "session_id": "string",
  "interview_type": "core_conceptual",
  "overall_score": 68,
  "scores": {
    "accuracy": {
      "score": 85,
      "max": 100,
      "weight": 0.40,
      "note": "Định nghĩa đúng, nhưng sai ở context switching overhead"
    },
    "depth": {
      "score": 60,
      "max": 100,
      "weight": 0.30,
      "note": "Dừng ở mức định nghĩa, chưa đến trade-off và use case"
    },
    "practical_application": {
      "score": 55,
      "max": 100,
      "weight": 0.20,
      "note": "Không liên hệ được với kinh nghiệm thực tế"
    },
    "clarity": {
      "score": 70,
      "max": 100,
      "weight": 0.10,
      "note": "Giải thích rõ ràng nhưng thiếu analogy"
    }
  },
  "concept_coverage": {
    "memory isolation": {
      "mentioned": true,
      "correct": true,
      "candidate_statement": "Process có memory riêng biệt còn thread dùng chung heap"
    },
    "context switching": {
      "mentioned": true,
      "correct": false,
      "candidate_statement": "Thread luôn nhanh hơn process vì context switch nhẹ hơn",
      "correction": "Không hoàn toàn đúng — với CPU-bound task, overhead GIL có thể làm thread chậm hơn"
    },
    "GIL": {
      "mentioned": false,
      "correct": null,
      "candidate_statement": null
    },
    "concurrency vs parallelism": {
      "mentioned": true,
      "correct": true,
      "candidate_statement": "Thread phù hợp concurrency, process phù hợp parallelism"
    }
  },
  "level_calibration": {
    "expected_level": "mid",
    "actual_demonstrated_level": "junior",
    "gap": "Trả lời đúng định nghĩa nhưng thiếu trade-off và use case thực tế theo yêu cầu mid-level"
  },
  "misconceptions": [
    {
      "claim": "Thread luôn nhanh hơn process",
      "correction": "Không đúng — với CPU-bound task trong Python, multiprocessing thường nhanh hơn do GIL lock thread lại"
    }
  ],
  "feedback": {
    "strengths": [
      "Nắm vững định nghĩa cơ bản, phân biệt đúng shared memory vs isolated memory",
      "Hiểu đúng concurrency vs parallelism ở mức high-level"
    ],
    "improvements": [
      "Cần đề cập GIL trong Python — quan trọng với role backend Python",
      "Nên nêu use case cụ thể: thread cho I/O-bound (DB calls, HTTP), process cho CPU-bound (image processing, ML inference)",
      "Tránh generalize: 'thread luôn nhanh hơn' — cần đặt trong context cụ thể"
    ],
    "key_points_to_study": [
      "Python GIL và cách bypass (multiprocessing, asyncio)",
      "Fork vs Spawn process model",
      "Thread safety và Race condition",
      "Async/await so với threading"
    ]
  },
  "meta": {
    "evaluated_at": "2024-01-15T10:30:00Z",
    "model_version": "evaluator-v1.0",
    "evaluation_duration_ms": 950
  },
  "summary": {
    "grade": "C",
    "hire_signal": "no",
    "one_line_verdict": "Kiến thức ở mức junior, chưa đạt depth kỳ vọng của mid-level — cần luyện thêm về trade-off và ứng dụng thực tế"
  }
}
```

---

## 4. Common Output Fields

Mọi output đều bao gồm 2 block chung:

### `meta` block

```json
{
  "meta": {
    "session_id": "string",
    "interview_type": "live_coding | behavioral | core_conceptual",
    "evaluated_at": "ISO8601 timestamp",
    "model_version": "string",
    "evaluation_duration_ms": 1200
  }
}
```

### `summary` block

```json
{
  "summary": {
    "overall_score": 74,
    "grade": "A | B | C | D | F",
    "hire_signal": "strong_yes | yes | weak_yes | no | strong_no",
    "one_line_verdict": "string"
  }
}
```

### Grade mapping

| Score | Grade | Hire Signal |
|-------|-------|-------------|
| 90–100 | A | strong_yes |
| 75–89  | B | yes |
| 60–74  | C | weak_yes |
| 45–59  | D | no |
| 0–44   | F | strong_no |

---

## Tóm tắt so sánh

| Tiêu chí | Live Coding | Behavioral | Core Conceptual |
|---|---|---|---|
| Có thể auto-score | Một phần (test runner) | Không | Không |
| Level trong input | Không (resolve ở backend) | Không (resolve ở backend) | Không (`depth_expected` pre-resolved) |
| Output chính | Code analysis | STAR breakdown | Concept coverage |
| Red flag chính | Suboptimal complexity | Missing Result | Misconceptions |
| Thời gian điển hình | 20–45 phút | 5–10 phút | 5–15 phút |
