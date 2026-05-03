"""Prompt builder for /follow-up/generate.

One prompt covers both BEHAVIORAL and CORE_CONCEPTUAL parents — the parent
type and weak_target.kind drive the framing. Live coding follow-ups are
not generated here (they would mean handing the user a different problem,
which is an orchestrator decision, not an LLM one).
"""
from __future__ import annotations

from models.follow_up import FollowUpRequest


def build_follow_up_prompt(req: FollowUpRequest) -> str:
    p = req.parent_question
    wt = req.weak_target

    lang_label = "VIETNAMESE" if req.language == "vi" else "ENGLISH"

    type_specific = ""
    if p.type == "BEHAVIORAL":
        signals = "\n".join(f"  - {s}" for s in (p.expected_signals or [])) or "  (none specified)"
        type_specific = f"""
PARENT QUESTION TYPE     : BEHAVIORAL (competency: {p.competency or 'unspecified'})
EXPECTED BEHAVIORAL SIGNALS:
{signals}
"""
    else:
        concepts = "\n".join(f"  - {c}" for c in (p.key_concepts or [])) or "  (none specified)"
        type_specific = f"""
PARENT QUESTION TYPE     : CORE_CONCEPTUAL (domain: {p.domain or 'unspecified'})
KEY CONCEPTS THE PARENT TARGETED:
{concepts}
"""

    strong_block = ""
    if req.strong_targets:
        strong_lines = "\n".join(f"  - [{t.kind}] {t.value}" for t in req.strong_targets)
        strong_block = f"""
ALREADY DEMONSTRATED (do not re-probe these):
{strong_lines}
"""

    target_kind_guidance = {
        "signal": (
            "The candidate did NOT demonstrate this expected behavioral signal in the parent answer. "
            "Your follow-up must directly elicit this specific signal — for example, if the missing "
            "signal is 'specific_recent_example', ask the candidate to ground a vague claim in a "
            "concrete recent incident with named people / dates / metrics."
        ),
        "concept": (
            "The candidate either did not mention this concept or mentioned it incorrectly in the "
            "parent answer. Your follow-up must put the concept directly in front of them — ask "
            "them to define it, walk through how it works, or compare it against the alternative "
            "they appeared to confuse it with."
        ),
        "misconception": (
            "The candidate stated something factually wrong tied to this misconception. Your "
            "follow-up should give them a chance to correct themselves: present a concrete scenario "
            "or counter-example that exposes the misconception, then ask them to explain what is "
            "happening. Do not lecture — let them work it out."
        ),
        "red_flag": (
            "A red flag of this type was detected in the parent answer (e.g. vague_action, "
            "blame_shifting). Your follow-up should probe specifically against the red flag — "
            "force the candidate to demonstrate the missing positive behaviour (e.g. for "
            "'vague_action', ask: 'Walk me through the specific things YOU did, not the team')."
        ),
    }[wt.kind]

    return f"""
You are an EXPERIENCED SENIOR INTERVIEWER. The candidate just answered a question and the
evaluator identified a specific gap. Your job is to write ONE follow-up question that probes
exactly that gap — nothing more, nothing wider.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE LANGUAGE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Write ALL output text in {lang_label}.
Keep `expected_points` items in {lang_label} too.
The `rationale` field is for system audit — also write it in {lang_label}.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PARENT QUESTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ID         : {p.id}
TEXT       : "{p.text}"
{type_specific}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CANDIDATE'S ANSWER (transcript)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
---
{req.user_answer_transcript}
---

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
WEAK TARGET TO PROBE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Kind      : {wt.kind}
Value     : {wt.value}
Severity  : {wt.severity}
Difficulty: {req.difficulty}

GUIDANCE:
{target_kind_guidance}
{strong_block}
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RULES FOR THE FOLLOW-UP
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. ONE question. Not two, not a list — a single focused question the user can speak/code an
   answer to.
2. Difficulty must match the requested {req.difficulty} level. Do not raise the bar above
   the parent. The candidate is being given a chance to recover, not stretched.
3. Reference the candidate's actual answer if helpful (e.g. "You mentioned X — explain
   what X means here"), but do not quote it word-for-word.
4. Do NOT re-ask anything the candidate already covered well — see ALREADY DEMONSTRATED
   list above (if any).
5. Do NOT lecture or hint at the answer. Ask, do not teach.
6. The question must be answerable in 30–90 seconds at this difficulty level.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OUTPUT FIELDS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
question_text   : The single follow-up question, written naturally as an interviewer
                  would speak it.
expected_points : 2–5 SPECIFIC bullets describing what a satisfactory answer must cover.
                  Be concrete (e.g. "Names a specific timeframe and at least one metric")
                  not vague (e.g. "Gives a good answer").
rationale       : 1–2 sentences explaining HOW this follow-up addresses {wt.kind}={wt.value}.
                  This is for system audit — be precise.
source          : Always exactly the string "AI_GENERATED".
model_meta      : Empty object — the service fills this in.
""".strip()
