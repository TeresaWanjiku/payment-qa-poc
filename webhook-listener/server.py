import hmac
import hashlib
import os
import json
import datetime
from flask import Flask, request, jsonify
from dotenv import load_dotenv

load_dotenv()

app = Flask(__name__)

received_references = set()

EVENTS_FILE = os.path.join(os.path.dirname(__file__), "events.json")


def append_event(event):
    # Read existing events
    if os.path.exists(EVENTS_FILE):
        with open(EVENTS_FILE, "r") as f:
            try:
                events = json.load(f)
            except json.JSONDecodeError:
                events = []
    else:
        events = []

    # Append new event with timestamp
    events.append({
        "received_at": datetime.datetime.utcnow().isoformat(),
        "event": event.get("event"),
        "reference": event.get("data", {}).get("reference"),
        "amount": event.get("data", {}).get("amount"),
        "currency": event.get("data", {}).get("currency"),
        "status": event.get("data", {}).get("status"),
        "raw": event
    })

    # Write back
    with open(EVENTS_FILE, "w") as f:
        json.dump(events, f, indent=2)

    print(f"  → Event written to events.json")


@app.route("/webhook", methods=["POST"])
def webhook():
    paystack_secret = os.getenv("PAYSTACK_SECRET_KEY")

    paystack_signature = request.headers.get("x-paystack-signature")
    raw_body = request.get_data()

    computed_hash = hmac.new(
        paystack_secret.encode("utf-8"),
        raw_body,
        hashlib.sha512
    ).hexdigest()

    if computed_hash != paystack_signature:
        timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
        print(f"[{timestamp}] [Webhook REJECTED] Signature mismatch — request did not come from Paystack")
        return jsonify({"status": "unauthorized"}), 401

    event = request.get_json()
    event_type = event.get("event")
    data = event.get("data", {})

    # Payload validation — reject if required fields are missing
    required_fields = ["reference", "amount", "currency", "status"]
    missing = [f for f in required_fields if not data.get(f)]

    if not event_type or missing:
        timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
        print(f"[{timestamp}] [Webhook REJECTED] Missing fields: event={event_type}, missing data fields={missing}")
        return jsonify({"status": "bad request", "missing": missing}), 400

    timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
    print(f"[{timestamp}] [Webhook VERIFIED] Event: {event_type}")

    reference = data.get("reference")
    if reference in received_references:
        timestamp = datetime.datetime.utcnow().strftime("%Y-%m-%d %H:%M:%S UTC")
        print(f"[{timestamp}] [Webhook REJECTED] Duplicate reference: {reference}")
        return jsonify({"status": "duplicate"}), 409

    received_references.add(reference)

    if event_type == "charge.success":
        amount = data.get("amount")
        currency = data.get("currency")
        status = data.get("status")
        reference = data.get("reference")
        print(f"[{timestamp}]  → Payment successful: {currency} {amount} | Ref: {reference} | Status: {status}")

    elif event_type == "charge.failed":
        print(f"[{timestamp}]  → Payment failed: {data}")

    else:
        print(f"[{timestamp}]  → Unhandled event type: {event_type}")

    append_event(event)

    return jsonify({"status": "ok"}), 200


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)