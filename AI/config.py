import os
from dotenv import load_dotenv

load_dotenv()

# ─── Gemini (chat + embedding) ────────────────────────────────────────────────
GOOGLE_API_KEY: str = os.getenv("GOOGLE_API_KEY", "")
MODEL_NAME: str = os.getenv("MODEL_NAME", "gemini-flash-latest")
THINKING_LEVEL: str = os.getenv("THINKING_LEVEL", "low")
EMBEDDING_MODEL: str = os.getenv("EMBEDDING_MODEL", "text-embedding-004")
EMBEDDING_DIM: int = int(os.getenv("EMBEDDING_DIM", "768"))

# ─── Evaluation graph ─────────────────────────────────────────────────────────
MAX_RETRIES: int = int(os.getenv("MAX_RETRIES", "2"))
GENERATOR_MAX_RETRIES: int = int(os.getenv("GENERATOR_MAX_RETRIES", "1"))
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

# Question-bank service base URL — used by the bootstrap reindex endpoint.
QUESTION_BANK_BASE_URL: str = os.getenv(
    "QUESTION_BANK_BASE_URL", "http://question-bank-service:8084"
)
QUESTION_BANK_INTERNAL_API_KEY: str = os.getenv("INTERNAL_API_KEY", "")

# ─── Auth for inbound calls ───────────────────────────────────────────────────
SERVICE_API_KEY: str = os.getenv("SERVICE_API_KEY", "")
