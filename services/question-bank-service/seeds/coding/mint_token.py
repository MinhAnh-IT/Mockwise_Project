#!/usr/bin/env python3
"""Mint a short-lived admin JWT (HS256) matching the IAM contract, so the
seed importer / smoke test can call the gateway-protected admin + practice
APIs. Secret comes from JWT_HMAC_SECRET (read off the iam container env).

  JWT_HMAC_SECRET=... python3 mint_token.py
"""
import base64
import hashlib
import hmac
import json
import os
import sys
import time
import uuid

# Admin identity (from prod iam.users, role=Admin, token_version=5).
SUB = os.getenv("ADMIN_SUB", "93a6789f-1192-480a-a479-957be8bb4fdd")
USERNAME = os.getenv("ADMIN_USERNAME", "anhhuynh.tech@gmail.com")
VER = int(os.getenv("ADMIN_TOKEN_VERSION", "5"))
SECRET = os.environ.get("JWT_HMAC_SECRET")


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def mint() -> str:
    if not SECRET:
        print("ERROR: JWT_HMAC_SECRET not set", file=sys.stderr)
        sys.exit(2)
    now = int(time.time())
    header = {"alg": "HS256"}
    payload = {
        "sub": SUB,
        "aud": "mockwise-api",
        "ver": VER,
        "nbf": now - 5,
        "role": "Admin",
        "iss": "mockwise-iam",
        "exp": now + 3600,
        "iat": now,
        "jti": str(uuid.uuid4()),
        "username": USERNAME,
    }
    signing_input = f"{_b64(json.dumps(header, separators=(',', ':')).encode())}." \
                    f"{_b64(json.dumps(payload, separators=(',', ':')).encode())}"
    sig = hmac.new(SECRET.encode(), signing_input.encode(), hashlib.sha256).digest()
    return f"{signing_input}.{_b64(sig)}"


if __name__ == "__main__":
    print(mint())
