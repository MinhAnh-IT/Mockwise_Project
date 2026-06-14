import os
from dotenv import load_dotenv

load_dotenv()

# ─── Gemini (chat + embedding) ────────────────────────────────────────────────
GOOGLE_API_KEY: str = os.getenv("GOOGLE_API_KEY", "")
MODEL_NAME: str = os.getenv("MODEL_NAME", "gemini-flash-latest")
THINKING_LEVEL: str = os.getenv("THINKING_LEVEL", "low")
EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "gemini-embedding-001")
EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "768"))

# ─── Evaluation graph ─────────────────────────────────────────────────────────
MAX_RETRIES: int = int(os.getenv("MAX_RETRIES", "2"))
GENERATOR_MAX_RETRIES: int = int(os.getenv("GENERATOR_MAX_RETRIES", "1"))
# When true, the generator runs the analyzer's reference solution through the
# real judge Python driver to derive each testcase's expectedOutput (instead of
# trusting the LLM's hand-computed value). See generator/nodes/expected_verifier.
VERIFY_EXPECTED_OUTPUTS: bool = os.getenv("VERIFY_EXPECTED_OUTPUTS", "true").lower() == "true"
# Wall-clock cap for executing the (LLM-written) reference solution over the whole
# testcase batch. A runaway/loop reference is abandoned and the LLM's expected
# outputs are kept, rather than hanging the request worker.
EXPECTED_VERIFY_TIMEOUT_SECONDS: int = int(os.getenv("EXPECTED_VERIFY_TIMEOUT_SECONDS", "15"))
# How many times to regenerate the reference/brute-force solutions when they
# disagree (or one fails) on some input — the expected-output repair loop.
REFERENCE_MAX_RETRIES: int = int(os.getenv("REFERENCE_MAX_RETRIES", "2"))
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
