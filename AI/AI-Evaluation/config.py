import os
from dotenv import load_dotenv

load_dotenv()

GOOGLE_API_KEY: str = os.getenv("GOOGLE_API_KEY", "")
MODEL_NAME: str = os.getenv("MODEL_NAME", "gemini-2.0-flash")
MAX_RETRIES: int = int(os.getenv("MAX_RETRIES", "2"))
MODEL_VERSION: str = os.getenv("MODEL_VERSION", "evaluator-v1.0")
SERVICE_API_KEY: str = os.getenv("SERVICE_API_KEY", "")
