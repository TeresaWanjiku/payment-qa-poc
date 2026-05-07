import hmac
import hashlib
import json
import os
import sys
import time
import urllib.request
import urllib.error
from dotenv import load_dotenv

load_dotenv()

SECRET = os.getenv("PAYSTACK_SECRET_KEY")
URL = "https://preocular-velva-typologic.ngrok-free.dev/webhook"
REF_FILE = os.path.join(os.path.dirname(__file__), "current-ref.txt")


def fire_webhook(reference):
    payload = {
        "event": "charge.success",
        "data": {
            "amount": 10000,
            "currency": "KES",
            "status": "success",
            "reference": reference
        }
    }
    body = json.dumps(payload, separators=(',', ':')).encode("utf-8")
    signature = hmac.new(SECRET.encode("utf-8"), body, hashlib.sha512).hexdigest()

    print(f"[Firing webhook] URL: {URL} | ref: {reference}")

    req = urllib.request.Request(
        URL,
        data=body,
        headers={
            "Content-Type": "application/json",
            "x-paystack-signature": signature
        },
        method="POST"
    )

    try:
        with urllib.request.urlopen(req) as res:
            response_body = res.read().decode("utf-8")
            print(f"[Webhook sent] ref: {reference} | Response: {res.status} | Body: {response_body}")
    except urllib.error.HTTPError as e:
        print(f"[Webhook FAILED] ref: {reference} | HTTP {e.code}: {e.reason}")
        print(f"  Response body: {e.read().decode('utf-8')}")
        raise
    except urllib.error.URLError as e:
        print(f"[Webhook FAILED] ref: {reference} | Network error: {e.reason}")
        raise


# If reference passed directly, use it. Otherwise watch the file.
if len(sys.argv) > 1:
    fire_webhook(sys.argv[1])
else:
    print("[Watcher] Watching for new reference in current-ref.txt ...")
    print(f"[Config] Webhook URL: {URL}")
    print(f"[Config] Secret key loaded: {'Yes' if SECRET else 'No'}")
    last_ref = None

    while True:
        if os.path.exists(REF_FILE):
            with open(REF_FILE, "r") as f:
                ref = f.read().strip()
            if ref and ref != last_ref:
                print(f"[Watcher] New reference detected: {ref}")
                fire_webhook(ref)
                last_ref = ref
        time.sleep(1)