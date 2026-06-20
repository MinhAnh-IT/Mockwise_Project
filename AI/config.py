import os
from dotenv import load_dotenv

load_dotenv()

# ─── Gemini (chat + embedding) ────────────────────────────────────────────────
GOOGLE_API_KEY: str = os.getenv("GOOGLE_API_KEY", "")
# Single model across the whole AI service (evaluator, selector, generator):
# gemini-3.5-flash — chosen for speed/cost. Output correctness on generated
# testcases is protected by expected_verifier (dual-solution consensus), so a
# Flash miscomputation gets caught and fixed rather than shipped.
MODEL_NAME: str = os.getenv("MODEL_NAME", "gemini-3.5-flash")
THINKING_LEVEL: str = os.getenv("THINKING_LEVEL", "low")
EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "gemini-embedding-001")
EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "768"))

# ─── Generator model (coding-question / testcase generation) ──────────────────
# Same model as the rest of the service now (was a dedicated pro model). Kept as a
# separate knob so the generator path can be pointed at a stronger model (e.g.
# gemini-3.1-pro-preview) independently if generation quality ever needs it.
# thinking_level is medium here (vs low elsewhere) since testcase/edge design is
# the heaviest reasoning step; drop to low for faster, cheaper generation.
GENERATOR_MODEL_NAME: str = os.getenv("GENERATOR_MODEL_NAME", "gemini-3.5-flash")
GENERATOR_THINKING_LEVEL: str = os.getenv("GENERATOR_THINKING_LEVEL", "medium")
# Upper bound on numTestcases. The generator emits all N cases in one structured
# call. With the bigger output-token budget + parallel verification, ~100 cases
# is the healthy ceiling for the one-shot path; past that the LLM output starts to
# truncate and latency climbs. Genuinely larger counts would need a programmatic
# bulk generator.
MAX_TESTCASES: int = int(os.getenv("MAX_TESTCASES", "100"))

# ─── Evaluation graph ─────────────────────────────────────────────────────────
MAX_RETRIES: int = int(os.getenv("MAX_RETRIES", "2"))
# Two retries (was 1): count-drift / duplicate-input violations on larger batches
# often clear on a second regeneration; the extra attempt is cheap relative to a
# shipped best-effort partial.
GENERATOR_MAX_RETRIES: int = int(os.getenv("GENERATOR_MAX_RETRIES", "2"))
# When true, the generator runs the analyzer's reference solution through the
# real judge Python driver to derive each testcase's expectedOutput (instead of
# trusting the LLM's hand-computed value). See generator/nodes/expected_verifier.
VERIFY_EXPECTED_OUTPUTS: bool = os.getenv("VERIFY_EXPECTED_OUTPUTS", "true").lower() == "true"
# Wall-clock cap for executing the (LLM-written) reference solution over the whole
# testcase batch. A runaway/loop reference is abandoned and the LLM's expected
# outputs are kept, rather than hanging the request worker.
EXPECTED_VERIFY_TIMEOUT_SECONDS: int = int(os.getenv("EXPECTED_VERIFY_TIMEOUT_SECONDS", "15"))
# How many testcases to verify CONCURRENTLY. Each case runs the reference (and
# brute) in its own subprocess; a thread pool dispatches several at once so a
# 100-case batch finishes in seconds instead of running 200 subprocesses serially.
# Default tuned to the VPS core count (6).
EXPECTED_VERIFY_WORKERS: int = int(os.getenv("EXPECTED_VERIFY_WORKERS", "6"))
# How many times to regenerate the reference/brute-force solutions when they
# disagree (or one fails) on some input — the expected-output repair loop.
REFERENCE_MAX_RETRIES: int = int(os.getenv("REFERENCE_MAX_RETRIES", "2"))
# ── Programmatic input generation (phase B) ───────────────────────────────────
# When true, an LLM-authored `gen_inputs(num_cases, seed)` Python function is run
# to synthesise the HIDDEN testcases' inputs PROGRAMMATICALLY (genuinely large /
# random, near constraint bounds) instead of having the model hand-type them —
# the existing expected_verifier then computes their outputs. Runs ONLY when the
# analyzer marked the problem unique_answer=true and expected-output verification
# is on (it relies on it to fill the swapped cases' outputs). Default OFF —
# enable in staging and smoke-test before prod.
PROGRAMMATIC_INPUTS: bool = os.getenv("PROGRAMMATIC_INPUTS", "false").lower() == "true"
# Fixed seed so the generator is deterministic across repair-loop re-runs.
PROGRAMMATIC_INPUT_SEED: int = int(os.getenv("PROGRAMMATIC_INPUT_SEED", "42"))
EVALUATOR_VERSION: str = os.getenv("EVALUATOR_VERSION", "evaluator-v1.0")
# Kept for backwards compatibility — older code reads MODEL_VERSION.
MODEL_VERSION: str = os.getenv("MODEL_VERSION", EVALUATOR_VERSION)

# ─── Question selector (RAG) ──────────────────────────────────────────────────
SELECTOR_VERSION: str = os.getenv("SELECTOR_VERSION", "selector-v1.0")
HARD_FILTER_LIMIT: int = int(os.getenv("HARD_FILTER_LIMIT", "50"))
TOP_K: int = int(os.getenv("TOP_K", "5"))

# Postgres + pgvector
DATABASE_URL: str = os.getenv("DATABASE_URL", "")
DB_POOL_MIN_SIZE: int = int(os.getenv("DB_POOL_MIN_SIZE", "2"))
DB_POOL_MAX_SIZE: int = int(os.getenv("DB_POOL_MAX_SIZE", "10"))

# Kafka — question-bank-events stream
KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "")
KAFKA_TOPIC_QUESTION_BANK: str = os.getenv(
    "KAFKA_TOPIC_QUESTION_BANK", "question-bank-events"
)
KAFKA_CONSUMER_GROUP: str = os.getenv(
    "KAFKA_CONSUMER_GROUP", "ai-service-selector"
)

# Kafka — evaluation pipeline (interview-service ↔ ai-service)
# Independent group id from the selector consumer so the two streams have
# separate offset tracking; sharing a group would let one slow handler stall
# the other.
KAFKA_TOPIC_EVALUATION_REQUESTED: str = os.getenv(
    "KAFKA_TOPIC_EVALUATION_REQUESTED", "evaluation-requested"
)
KAFKA_TOPIC_EVALUATION_COMPLETED: str = os.getenv(
    "KAFKA_TOPIC_EVALUATION_COMPLETED", "evaluation-completed"
)
KAFKA_TOPIC_EVALUATION_FAILED: str = os.getenv(
    "KAFKA_TOPIC_EVALUATION_FAILED", "evaluation-failed"
)
KAFKA_EVALUATION_CONSUMER_GROUP: str = os.getenv(
    "KAFKA_EVALUATION_CONSUMER_GROUP", "ai-service-evaluator"
)

# Kafka — overall (cross-question) review pipeline. Fired by interview-service
# after every per-answer evaluation has reached a terminal state for a session.
KAFKA_TOPIC_SESSION_EVAL_REQUESTED: str = os.getenv(
    "KAFKA_TOPIC_SESSION_EVAL_REQUESTED", "session-evaluation-requested"
)
KAFKA_TOPIC_SESSION_EVAL_COMPLETED: str = os.getenv(
    "KAFKA_TOPIC_SESSION_EVAL_COMPLETED", "session-evaluation-completed"
)
KAFKA_TOPIC_SESSION_EVAL_FAILED: str = os.getenv(
    "KAFKA_TOPIC_SESSION_EVAL_FAILED", "session-evaluation-failed"
)
KAFKA_SESSION_EVAL_CONSUMER_GROUP: str = os.getenv(
    "KAFKA_SESSION_EVAL_CONSUMER_GROUP", "ai-service-session-reviewer"
)

# Question-bank service base URL — used by the bootstrap reindex endpoint.
QUESTION_BANK_BASE_URL: str = os.getenv(
    "QUESTION_BANK_BASE_URL", "http://question-bank-service:8084"
)
QUESTION_BANK_INTERNAL_API_KEY: str = os.getenv("INTERNAL_API_KEY", "")

# ─── Auth for inbound calls ───────────────────────────────────────────────────
SERVICE_API_KEY: str = os.getenv("SERVICE_API_KEY", "")
