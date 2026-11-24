#!/usr/bin/env python3
"""
Saqr Code license token tool (v0.11) · أداة تراخيص صقر كود

Mints the offline license tokens the app accepts on the paywall
("License token (SQ1.…)"). Workflow after a Moyasar payment lands:

  1. Read the invoice: paid? (Moyasar dashboard or the app's merchant tools)
  2. Mint a token with the SAME licenseKey that is in keystore.properties:

     SAQR_LICENSE_KEY=<64 hex chars> python3 scripts/license_tool.py \
         --tier pro --email user@example.com --order inv_abc123 --days 31

     lifetime:   --tier lifetime          (no --days)
     student:    --tier student --days 31

  3. Send the printed SQ1.… token to the customer; they paste it in
     Settings → Plans & licensing → "Activate token".

Requires: python3 (stdlib only).
"""
import argparse
import base64
import hashlib
import hmac
import json
import os
import secrets
import sys
import time

VERSION = "SQ1"
TIERS = {"pro", "student", "lifetime"}
MONTH_MS = 31 * 24 * 60 * 60 * 1000


def b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode().rstrip("=")


def unb64url(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def generate_key() -> str:
    return secrets.token_hex(32)


def mint(tier: str, email, order: str, expires_at, key: bytes) -> str:
    payload = {
        "v": 1,
        "tier": tier,
        "email": email,
        "order": order,
        "exp": expires_at,
        "iat": int(time.time() * 1000),
    }
    body = json.dumps(payload, separators=(",", ":"), ensure_ascii=False).encode()
    sig = hmac.new(key, body, hashlib.sha256).digest()
    return f"{VERSION}.{b64url(body)}.{b64url(sig)}"


def verify(token: str, key: bytes) -> dict:
    parts = token.strip().split(".")
    if len(parts) != 3 or parts[0] != VERSION:
        raise ValueError("malformed")
    body = unb64url(parts[1])
    if not hmac.compare_digest(unb64url(parts[2]), hmac.new(key, body, hashlib.sha256).digest()):
        raise ValueError("bad signature")
    payload = json.loads(body.decode())
    exp = payload.get("exp")
    if exp and time.time() * 1000 > exp:
        raise ValueError("expired")
    return payload


def read_key(args) -> bytes:
    key_hex = args.key or os.environ.get("SAQR_LICENSE_KEY")
    if not key_hex and os.path.exists("keystore.properties"):
        for line in open("keystore.properties"):
            if line.startswith("licenseKey="):
                key_hex = line.split("=", 1)[1].strip()
                break
    if not key_hex:
        sys.exit("no key: pass --key, set SAQR_LICENSE_KEY, or run from the repo root")
    key_hex = key_hex.strip().lower()
    if len(key_hex) < 64 or len(key_hex) % 2:
        sys.exit("key must be at least 64 hex chars (32 bytes)")
    return bytes.fromhex(key_hex[: 2 * (len(key_hex) // 2)])


def main() -> None:
    ap = argparse.ArgumentParser(description="Saqr Code license token tool")
    ap.add_argument("--tier", choices=sorted(TIERS), help="plan tier")
    ap.add_argument("--email", default=None, help="customer email (optional)")
    ap.add_argument("--order", default="manual", help="order/invoice id for your records")
    ap.add_argument("--days", type=int, default=None, help="validity in days (omit for lifetime)")
    ap.add_argument("--key", default=None, help="HMAC key as 64 hex chars (else env/keystore.properties)")
    ap.add_argument("--genkey", action="store_true", help="generate a fresh key and exit")
    ap.add_argument("--verify", metavar="TOKEN", default=None, help="verify a token instead of minting")
    args = ap.parse_args()

    if args.genkey:
        k = generate_key()
        print(k)
        print("# add this to keystore.properties as: licenseKey=" + k, file=sys.stderr)
        return

    key = read_key(args)

    if args.verify:
        try:
            print(json.dumps(verify(args.verify, key), indent=2))
        except Exception as e:
            sys.exit(f"INVALID: {e}")
        return

    if not args.tier:
        sys.exit("--tier is required")
    expires = int(time.time() * 1000) + args.days * 24 * 60 * 60 * 1000 if args.days else None
    if args.tier == "lifetime" and expires:
        sys.exit("lifetime tokens take no --days")
    if args.tier in ("pro", "student") and not expires:
        expires = int(time.time() * 1000) + MONTH_MS  # default: one month
    token = mint(args.tier, args.email, args.order, expires, key)
    print(token)
    print(f"# tier={args.tier} email={args.email or '-'} order={args.order} exp={expires}", file=sys.stderr)


if __name__ == "__main__":
    main()
