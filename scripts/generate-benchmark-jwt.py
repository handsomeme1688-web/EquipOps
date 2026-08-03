#!/usr/bin/env python3
"""Generate a short-lived local benchmark JWT compatible with JJWT 0.9.1."""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import json
import os
import time


def base64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def decode_jjwt_secret(value: str) -> bytes:
    # JwtUtil uses JJWT's signWith(algorithm, String), whose String overload
    # treats the configured value as a Base64-encoded key.
    encoded = value.strip().encode("ascii")
    encoded += b"=" * (-len(encoded) % 4)
    return base64.b64decode(encoded, altchars=b"-_", validate=False)


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate an EquipOps benchmark JWT")
    parser.add_argument("--user-id", type=int, default=1)
    parser.add_argument("--dept-id", type=int, default=1)
    parser.add_argument("--expires-in", type=int, default=7200)
    args = parser.parse_args()

    secret = os.getenv("JWT_SECRET")
    if not secret:
        parser.error("JWT_SECRET is not set")
    if args.expires_in <= 0:
        parser.error("--expires-in must be positive")

    header = {"alg": "HS256"}
    payload = {
        "userId": args.user_id,
        "deptId": args.dept_id,
        "exp": int(time.time()) + args.expires_in,
    }
    header_part = base64url(
        json.dumps(header, separators=(",", ":")).encode("utf-8")
    )
    payload_part = base64url(
        json.dumps(payload, separators=(",", ":")).encode("utf-8")
    )
    signing_input = f"{header_part}.{payload_part}".encode("ascii")
    signature = hmac.new(
        decode_jjwt_secret(secret), signing_input, hashlib.sha256
    ).digest()
    print(f"{signing_input.decode('ascii')}.{base64url(signature)}")


if __name__ == "__main__":
    main()
