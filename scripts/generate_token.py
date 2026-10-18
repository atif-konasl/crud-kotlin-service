#!/usr/bin/env python3
import base64
import json
import os
import subprocess
import time

script_dir = os.path.dirname(os.path.abspath(__file__))
key_path = os.path.join(script_dir, "private_key.pem")

header = {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "dev-key-1"
}

now = int(time.time())
payload = {
    "sub": "local-developer",
    "iss": "http://localhost:8081",
    "iat": now,
    "exp": now + 86400 * 30, # valid 30 days
    "scope": "read write"
}

def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")

encoded_header = b64url(json.dumps(header, separators=(",", ":")).encode("utf-8"))
encoded_payload = b64url(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
signing_input = f"{encoded_header}.{encoded_payload}".encode("ascii")

p = subprocess.Popen(
    ["openssl", "dgst", "-sha256", "-sign", key_path],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE
)
sig, _ = p.communicate(signing_input)
encoded_sig = b64url(sig)

token = f"{encoded_header}.{encoded_payload}.{encoded_sig}"
print(token)
