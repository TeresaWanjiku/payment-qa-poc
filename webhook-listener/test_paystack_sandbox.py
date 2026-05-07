import requests
import json
import os
import time
from dotenv import load_dotenv

load_dotenv()

PAYSTACK_SECRET = os.getenv("PAYSTACK_SECRET_KEY")
PAYSTACK_BASE_URL = "https://api.paystack.co"

def initialize_transaction():
    """Initialize a transaction using Paystack API"""

    url = f"{PAYSTACK_BASE_URL}/transaction/initialize"

    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET}",
        "Content-Type": "application/json"
    }

    # Use a test email and amount
    payload = {
        "email": "test@example.com",
        "amount": 10000,  # 100 KES (in kobo/cents)
        "currency": "KES",
        "reference": f"test_{int(time.time())}",  # Unique reference
        "callback_url": "https://example.com/callback"
    }

    print(f"[Init] Initializing transaction with Paystack...")
    print(f"  Reference: {payload['reference']}")

    response = requests.post(url, headers=headers, json=payload)

    if response.status_code == 200:
        data = response.json()
        print(f"[Success] Transaction initialized!")
        print(f"  Authorization URL: {data['data']['authorization_url']}")
        print(f"  Access Code: {data['data']['access_code']}")
        print(f"  Reference: {data['data']['reference']}")
        return data['data']
    else:
        print(f"[Error] Failed to initialize: {response.status_code}")
        print(f"  Response: {response.text}")
        return None


def verify_transaction(reference):
    """Verify transaction status"""

    url = f"{PAYSTACK_BASE_URL}/transaction/verify/{reference}"

    headers = {
        "Authorization": f"Bearer {PAYSTACK_SECRET}"
    }

    print(f"\n[Verify] Checking transaction status...")

    response = requests.get(url, headers=headers)

    if response.status_code == 200:
        data = response.json()
        status = data['data']['status']
        amount = data['data']['amount']
        print(f"  Status: {status}")
        print(f"  Amount: {amount}")
        return data['data']
    else:
        print(f"[Error] Verification failed: {response.status_code}")
        print(f"  Response: {response.text}")
        return None


def simulate_payment(reference):
    """
    For sandbox/test mode, you need to manually complete the payment.
    This function gives you instructions.
    """

    print(f"\n{'='*60}")
    print(f"MANUAL STEP REQUIRED - SANDBOX PAYMENT")
    print(f"{'='*60}")
    print(f"\nTo complete this test payment in Paystack sandbox:")
    print(f"\n1. Use these test card details:")
    print(f"   Card Number: 5060 6666 6666 6666 654")
    print(f"   Expiry: Any future date (e.g., 12/25)")
    print(f"   CVV: 123")
    print(f"   PIN: 1234")
    print(f"   OTP: 123456")
    print(f"\n2. OR use the Paystack dashboard:")
    print(f"   - Go to https://dashboard.paystack.com/#/transactions")
    print(f"   - Find transaction: {reference}")
    print(f"   - Click 'Mark as Successful' (in test mode)")
    print(f"\nWaiting for payment completion...")


if __name__ == "__main__":
    print("="*60)
    print("PAYSTACK SANDBOX TEST - END TO END")
    print("="*60)

    # Step 1: Initialize transaction
    transaction = initialize_transaction()

    if not transaction:
        print("\n[FAILED] Could not initialize transaction")
        exit(1)

    reference = transaction['reference']

    # Step 2: Instructions for manual payment
    simulate_payment(reference)

    # Step 3: Poll for completion
    max_attempts = 60  # Wait up to 60 seconds
    for i in range(max_attempts):
        time.sleep(2)

        result = verify_transaction(reference)

        if result and result['status'] == 'success':
            print(f"\n✅ PAYMENT SUCCESSFUL!")
            print(f"\n[Next Step] Check your server.py terminal for webhook receipt")
            print(f"[Next Step] Check events.json for the logged event")
            break

        if i % 5 == 0:
            print(f"  Still waiting... ({i*2}s elapsed)")
    else:
        print(f"\n⏰ Timeout - payment not completed within {max_attempts*2} seconds")
        print(f"\nYou can still manually verify later using:")
        print(f"  curl -H 'Authorization: Bearer {PAYSTACK_SECRET[:20]}...' \\")
        print(f"       {PAYSTACK_BASE_URL}/transaction/verify/{reference}")