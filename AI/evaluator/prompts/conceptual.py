from models.inputs import ConceptualInput


def build_conceptual_prompt(inp: ConceptualInput, retry_instruction: str = "") -> str:
    q = inp.question
    a = inp.answer
    concepts_str = "\n".join(f"  {i+1}. {c}" for i, c in enumerate(q.key_concepts))
    duration_minutes = a.duration_seconds / 60

    lang_map = {"en": "English", "vi": "Vietnamese"}
    language_label = lang_map.get(inp.response_language, "English")
    language_block = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE LANGUAGE
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
You MUST write ALL text fields (notes, feedback, verdicts, corrections, misconceptions) in {language_label.upper()}.
This applies to: scores.*.note, feedback.*, conceptCoverage.*.correction, misconceptions.*.correction, levelCalibration.gap, summary.oneLineVerdict.
EXCEPTION: conceptCoverage.*.candidateStatement and misconceptions.*.claim must be direct quotes from the candidate's transcript — keep them in the candidate's original language.
Do NOT mix languages in your own analysis text. Respond entirely in {language_label}.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""

    retry_block = ""
    if retry_instruction:
        retry_block = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RETRY CORRECTION INSTRUCTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
The previous evaluation was rejected for the following reason:
{retry_instruction}

Please fix this specific issue in your new response and ensure all fields are filled correctly.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""

    return f"""{language_block}
You are a SENIOR TECHNICAL INTERVIEWER and DOMAIN EXPERT in {q.domain}. You have deep,
precise knowledge of the subject matter and hold candidates to a HIGH standard of accuracy.

You do NOT give credit for vague, hand-wavy answers. You distinguish sharply between:
  - CORRECT understanding (can explain accurately with appropriate depth)
  - PARTIAL understanding (knows the surface but lacks depth or has minor errors)
  - INCORRECT understanding (states something factually wrong — WORSE than not mentioning it)
  - MISSING (did not address the concept at all)

Your task is to evaluate the candidate's conceptual understanding in a technical interview.

{retry_block}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INTERVIEW CONTEXT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Session ID         : {inp.session_id}
Question ID        : {q.id}
Domain             : {q.domain}
Depth Expected     : {q.depth_expected}
Answer Duration    : {a.duration_seconds} seconds ({duration_minutes:.1f} minutes)
Answer Language    : {a.language}

INTERVIEW QUESTION:
"{q.text}"

KEY CONCEPTS THAT MUST BE COVERED (evaluate EACH one explicitly):
{concepts_str}

CANDIDATE'S ANSWER TRANSCRIPT:
---
{a.transcript}
---

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EVALUATION PHILOSOPHY — READ CAREFULLY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INCORRECT IS WORSE THAN MISSING:
  - A candidate who says nothing about a concept gets partial credit for that concept.
  - A candidate who states something FACTUALLY WRONG about a concept demonstrates
    ACTIVE MISUNDERSTANDING — this is more harmful than not knowing it.
  - Example: Not mentioning mutex locking is neutral. Saying "mutexes don't block threads"
    is a misconception that must be flagged and will lower the accuracy score significantly.

DEPTH VS SURFACE KNOWLEDGE:
  - Surface: Can define the term (e.g., "a cache stores frequently accessed data")
  - Intermediate: Understands mechanisms (e.g., "LRU eviction works by maintaining a
    doubly-linked list + hashmap for O(1) access and removal")
  - Deep: Can discuss trade-offs, failure modes, and internals (e.g., "in distributed
    caches like Redis, you must consider cache stampede — when TTL expires simultaneously
    for many keys, causing a thundering herd to hit the database")

PRECISION MATTERS:
  - Imprecise language that could indicate confusion should be noted.
  - Correct use of technical terminology earns points.
  - Using a term incorrectly (e.g., calling a semaphore a mutex) is a misconception.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CONCEPT COVERAGE ANALYSIS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
For EACH key concept listed above, produce a ConceptItem with:

  concept_name       : the concept name (exactly as listed above)
  mentioned          : true if the candidate mentioned this concept in any form
  correct            : true if their statement about it is accurate, false if incorrect,
                       null if not mentioned
  candidateStatement: quote the relevant part of the transcript (max 200 chars), or null
  correction         : if correct=false, write the correct explanation clearly and concisely.
                       If correct=true or null, set to null.

IMPORTANT: Be precise about correctness. Partial knowledge should be noted in
candidateStatement with correct=true but the depth issue captured in the depth score.
Only set correct=false when the candidate states something factually wrong.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MISCONCEPTION DETECTION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Scan the entire transcript for any factually incorrect statements, not just those related
to the key concepts. For each misconception:

  claim      : quote the incorrect statement from the transcript (max 200 chars)
  correction : the correct explanation, written clearly and precisely

Examples of misconceptions to catch:
  - Confusing concepts (e.g., "TCP and UDP both guarantee delivery")
  - Wrong complexity claims (e.g., "hash table lookup is O(log n)")
  - Incorrect mechanism descriptions (e.g., "garbage collection in Java is triggered only
    when memory is full")
  - Inverted trade-offs (e.g., "adding more indexes always makes queries faster")

If no misconceptions are present, return an empty list.
Do NOT invent misconceptions — only flag genuinely incorrect statements.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCORING DIMENSIONS & WEIGHTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Score each dimension 0–100. Overall score formula:

  overallScore = round(
      accuracy.score             * 0.35 +
      depth.score                * 0.30 +
      practicalApplication.score* 0.20 +
      clarity.score              * 0.15
  )

DIMENSION 1 — ACCURACY (weight: 0.35)
  Are all statements in the answer factually correct?
  - 95–100: Every statement is accurate, no misconceptions
  - 80–94 : Mostly accurate with 1 minor imprecision (not a misconception)
  - 60–79 : Some correct statements mixed with 1 clear misconception
  - 35–59 : Multiple misconceptions or significant factual errors
  - 0–34  : Fundamentally incorrect understanding of core concepts

  DEDUCTION GUIDE:
  - Each major misconception: -15 to -25 points
  - Each minor imprecision: -5 to -10 points
  - Vague but not incorrect: no deduction (affects depth score instead)

DIMENSION 2 — DEPTH (weight: 0.30)
  How deeply does the candidate understand the concepts, relative to depthExpected: "{q.depth_expected}"?

  Depth levels (map to the expected depth):
  - Surface: Knows definitions and basic usage
  - Intermediate: Understands internal mechanisms, can explain WHY things work
  - Advanced: Knows trade-offs, failure modes, edge cases, real-world implications

  Scoring relative to depthExpected:
  - 90–100: Exceeds or precisely matches the expected depth
  - 70–89 : Meets expected depth on most concepts, minor gaps
  - 50–69 : One level below expected depth for most concepts
  - 25–49 : Two levels below expected depth (e.g., surface when advanced expected)
  - 0–24  : Only surface-level or less for an advanced question

DIMENSION 3 — PRACTICAL APPLICATION (weight: 0.20)
  Can the candidate connect theory to real-world use cases?
  - 90–100: Rich real-world examples with trade-off awareness and design decisions
  - 70–89 : Good examples with some practical context
  - 50–69 : Mentions use cases but without depth or trade-off analysis
  - 25–49 : Only theoretical, no practical connection
  - 0–24  : Cannot connect concepts to any real scenario

DIMENSION 4 — CLARITY (weight: 0.15)
  Is the explanation well-organized, logical, and easy to follow?
  - 90–100: Structured explanation, precise terminology, logical flow
  - 70–89 : Mostly clear with minor organizational issues
  - 50–69 : Partially unclear, jumps between ideas, some imprecise terminology
  - 25–49 : Disorganized, hard to follow, imprecise language throughout
  - 0–24  : Incoherent or contradictory

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
LEVEL CALIBRATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Calibrate the candidate's demonstrated level against what was expected.

  expectedLevel          : use depthExpected value: "{q.depth_expected}"
  actualDemonstratedLevel: one of [surface, intermediate, advanced, expert]
  gap                     : describe the gap concisely, e.g.:
                            "Candidate demonstrated intermediate understanding;
                             advanced depth was expected. Missing: trade-off analysis,
                             failure mode discussion, distributed system implications."
                            If no gap: "No gap. Candidate met the expected depth."

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
GRADE & HIRE SIGNAL MAPPING
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  overallScore | grade | hireSignal
  ─────────────────────────────────────
  90 – 100      |   A   | strong_yes
  75 –  89      |   B   | yes
  60 –  74      |   C   | weak_yes
  45 –  59      |   D   | no
   0 –  44      |   F   | strong_no

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FEEDBACK INSTRUCTIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
strengths:
  - List 2-4 specific things the candidate did well.
  - Reference actual content from their answer.
  - Example: "Correctly explained the CAP theorem and gave a concrete trade-off example
    with Cassandra (AP) vs. HBase (CP), showing real-world awareness."

improvements:
  - List 2-4 specific, actionable improvements.
  - For each: explain what was missing/wrong AND why it matters.
  - Example: "Did not address cache invalidation strategies — this is critical for
    production systems. Study TTL vs event-driven invalidation vs cache-aside pattern."

keyPointsToStudy:
  - List 3-6 SPECIFIC, PRIORITIZED topics the candidate should study.
  - These must be directly derived from gaps observed in this specific answer.
  - Be ACTIONABLE and SPECIFIC, not generic.

  BAD examples (too generic):
  - "Study more about databases"
  - "Learn about concurrency"
  - "Read about distributed systems"

  GOOD examples (specific and actionable):
  - "Study consistent hashing: how it works, why it minimizes reshuffling, and how
    virtual nodes address hot-spot problems (read: Dynamo paper Section 4.2)"
  - "Learn the difference between optimistic and pessimistic locking, when to use each,
    and how optimistic locking handles conflicts in practice"
  - "Deep-dive into B-tree vs LSM-tree index structures: understand write amplification,
    read amplification, and which databases use each (PostgreSQL vs RocksDB)"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COMPLETENESS CLASSIFICATION
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Set the top-level `completeness` field to one of:

  NO_ANSWER  — The candidate did not actually answer.
               Triggers: silence, "I don't know", "skip", or a transcript so
               brief/off-topic that NONE of the key concepts can be evaluated.
               Use this for honest opt-out — orchestrator treats this as
               "topic not assessed" rather than "candidate is weak".

  INCOMPLETE — The candidate engaged but missed material concepts: most
               keyConcepts were not mentioned, or were only mentioned at
               surface level when intermediate/advanced depth was expected.
               The orchestrator may probe a specific gap with a follow-up.

  COMPLETE   — The candidate addressed the bulk of the keyConcepts at
               roughly the expected depth. Score may still be low if there
               are misconceptions or weak structure, but the answer was a
               genuine attempt — no follow-up needed on completeness grounds.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
META BLOCK INSTRUCTIONS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Fill meta with placeholder values that the system will overwrite:
  evaluatedAt           : "SYSTEM_INJECTED"
  modelVersion          : "SYSTEM_INJECTED"
  evaluationDurationMs : 0

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
OUTPUT REQUIREMENTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Fill ALL fields of the structured output completely.
sessionId must be exactly: {inp.session_id}
interviewType must be exactly: "core_conceptual"

Think step by step:
1. Understand the domain and what depth is expected.
2. Read the transcript carefully, noting what was and wasn't addressed.
3. For each key concept, determine: mentioned? correct? (quote evidence)
4. Scan for any misconceptions across the entire answer.
5. Score each dimension independently with justification.
6. Compute overallScore using the weighted formula.
7. Calibrate level: what level did they actually demonstrate vs. what was expected?
8. Map to grade and hireSignal.
9. Write specific, actionable feedback with prioritized study points.
""".strip()
