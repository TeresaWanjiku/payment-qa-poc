# Payment QA — Strategy & Setup Guide

This project tests a Paystack payment integration using two things running side by side: a Java test suite that hits the Paystack sandbox API, and a Python webhook listener that receives and validates payment confirmation events.

---

## Problem Statement

Payment API testing is different from testing a regular consumer app because failures are often silent and delayed. A UI bug is visible immediately. A payment bug - a double charge, a missed webhook, a reconciliation mismatch - can go unnoticed for hours or days, and by then real money is involved.

The other difference is that payment flows span multiple systems. Your app talks to Paystack, Paystack talks to a bank, the bank sends a response, Paystack fires a webhook back to you. Each handoff is a failure point. Testing only your own API calls misses half the flow - which is why this project also validates the webhook leg of every transaction.

---

## Risk Register

| Failure Mode | What it means | Covered by                                                                          |
|---|---|-------------------------------------------------------------------------------------|
| Double charge | Same reference charged twice | TC10 - asserts duplicate reference returns 400                                      |
| Webhook misfire | Payment succeeds but webhook never arrives or arrives with wrong data | TC03, TC04 - assert webhook payload matches initialized reference, amount, currency |
| Auth bypass | Requests succeed without a valid API key | TC07 - asserts invalid key returns 401                                              |
| Timeout orphan | Payment initialized but never completed, leaving a dangling reference | TC09, TC11 - assert unrecognized references return 400                              |
| Reconciliation drift | Response fields missing or incorrect, causing downstream data mismatches | TC02, TC12 - assert full response schema and filtered transaction data              |

---

## Coverage Gaps

This PoC covers the API and webhook layers in a sandbox environment. It does not cover:

**Load testing** - no tests simulate concurrent transactions or measure how the integration holds up under volume.

**Full end-to-end with real checkout** - TC03 and TC04 use a simulated webhook rather than a real completed payment through Paystack's checkout UI. A real sandbox completion would require browser automation.

**Mobile SDK** - the Paystack mobile SDK has its own initialization and callback flow which is not tested here.

**Refunds and disputes** - the `refund` and `dispute` webhook events are not handled or tested.

**Production parity** - all tests run against Paystack sandbox. Sandbox behaviour does not always match production exactly, particularly around edge cases in bank responses.

---

## How to Scale

To grow this framework with a real team, the first step is CI/CD integration - running `gradle test` on every pull request, with the Flask listener and watcher spun up as part of the pipeline using a persistent tunnel like ngrok's reserved domains or a deployed test server. From there, test environments should be separated so sandbox tests don't share state across developers. As transaction volume grows, the in-memory replay prevention in `server.py` would need to move to a database, and alerting should be added so webhook failures surface in Slack or PagerDuty rather than sitting silently in a log file.

---

## Getting started

You need three terminals running at the same time.

**Terminal 1 - start the webhook listener:**
```
python webhook-listener\server.py
```

**Terminal 2 - start the watcher:**
```
python webhook-listener\test_webhook.py
```
The watcher monitors `current-ref.txt` and automatically fires a test webhook when the Java tests initialize a new payment. Without this running, TC03 and TC04 will time out.

**Terminal 3 - run the tests:**
```
gradle test
```

---

## Environment setup

Create a `.env` file at the project root (this file is gitignored):
```
PAYSTACK_SECRET_KEY=sk_test_your_key_here
BASE_URL=https://api.paystack.co
```

Install Python dependencies:
```
pip install flask python-dotenv
```

Install and authenticate ngrok:
```
ngrok config add-authtoken YOUR_TOKEN
ngrok http 5000
```

Paste the ngrok forwarding URL into Paystack Dashboard → Settings → API Keys & Webhooks, and update the `URL` variable in `test_webhook.py`. You'll need to do this every time ngrok restarts.

---

## Test cases

**TC01** - Valid payment initialization returns 200 with a reference.

**TC02** - Response schema includes authorization_url, access_code, and reference.

**TC03** - After initializing a payment, a `charge.success` webhook arrives with the correct reference, amount, and currency.

**TC04** - Transaction verify returns 200 with the correct reference. Webhook payload is also verified.

**TC05** - Missing amount field returns 400.

**TC06** - Missing email field returns 400.

**TC07** - Invalid API key returns 401.

**TC08** - Zero amount returns 400.

**TC09** - Non-existent reference returns 400.

**TC10** - Duplicate reference: first request succeeds, second returns 400.

**TC11** - Non-existent reference variant returns 400.

**TC12** - Filtering transactions by status=success returns only successful transactions.

---

## Webhook listener behaviour

The listener at `POST /webhook` does three things before processing any event:

**Signature check** - Paystack signs every webhook with your secret key. The listener recomputes the signature from the raw request body and compares it to the `x-paystack-signature` header. Anything that doesn't match is rejected with 401.

**Payload check** - The event must include `reference`, `amount`, `currency`, and `status` inside the `data` object. Missing fields return 400.

**Duplicate check** - References are tracked in memory. Sending the same reference twice returns 409. Note this resets when the server restarts.

Verified events are written to `webhook-listener/events.json` with a timestamp.

---

## Files

| File | What it does |
|------|-------------|
| `webhook-listener/server.py` | The Flask webhook receiver |
| `webhook-listener/test_webhook.py` | Simulates Paystack webhooks for local testing |
| `webhook-listener/events.json` | Log of received events, polled by Java tests |
| `webhook-listener/current-ref.txt` | Written by Java tests, read by the watcher |
| `src/test/java/TransactionTest.java` | All test cases |
| `src/test/java/WebhookTestHelper.java` | Waits for a specific reference to appear in events.json |
| `src/test/java/BaseTest.java` | Loads .env and sets the Paystack base URL |

---

## Known limitations

**Sandbox transactions show as abandoned** - Paystack marks initialized payments as abandoned unless they go through a real checkout flow. TC04 verifies the API responds correctly rather than asserting a success status.

**Replay prevention resets on restart** - The duplicate reference check uses an in-memory set, so it clears when Flask restarts.

**ngrok URL changes on restart** - Update `test_webhook.py` and the Paystack dashboard webhook URL whenever ngrok is restarted.

**Webhooks are simulated** - TC03 and TC04 use the watcher to simulate Paystack's webhook rather than triggering a real sandbox payment. Real end-to-end would require completing a payment through Paystack's checkout.