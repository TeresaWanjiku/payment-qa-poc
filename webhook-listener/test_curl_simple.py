import hmac
import hashlib
import json
import os
import requests
from dotenv import load_dotenv

load_dotenv()

SECRET = os.getenv("PAYSTACK_SECRET_KEY")
URL = "https://preocular-velva-typologic.ngrok-free.dev/webhook"

payload = {
    "event": "charge.success",
    "data": {
        "amount": 10000,
        "currency": "KES",
        "status": "success"
    }
}

body = json.dumps(payload, separators=(',', ':')).encode("utf-8")
signature = hmac.new(SECRET.encode("utf-8"), body, hashlib.sha512).hexdigest()

print(f"[DEBUG] Secret loaded: {SECRET[:10]}... (length: {len(SECRET)})")
print(f"[DEBUG] Signature generated: {signature[:20]}...")
print(f"[DEBUG] Sending request to: {URL}\n")

response = requests.post(
    URL,
    data=body,
    headers={
        "Content-Type": "application/json",
        "x-paystack-signature": signature
    }
)

print(f"Response: {response.status_code} {response.text}")