import os
from google import genai
from google.genai import types
from pydantic import BaseModel

from typing import Literal, get_args

CATEGORY_TYPE = Literal[
    "FOOD", "TRANSPORT", "SHOPPING", "BILLS",
    "ENTERTAINMENT", "HEALTH",
    "SALARY", "FREELANCE", "INVESTMENT", "GIFT",
    "OTHERS",
]
ALLOWED_CATEGORIES = set(get_args(CATEGORY_TYPE))

class SmsClassification(BaseModel):
    label: Literal["expense", "income", "irrelevant"]
    confidence: float
    category: CATEGORY_TYPE


# client = genai.Client(api_key=os.environ["GEMINI_API_KEY"])
def get_client():
    return genai.Client(api_key=os.environ["GEMINI_API_KEY"])
PROMPT_TEMPLATE = """You classify SMS messages for a personal finance app.

Return three fields: label, confidence, category.

LABEL (choose exactly one):
- "expense": money was spent, debited, paid, purchased, or withdrawn
- "income": money was received, credited, refunded, or earned
- "irrelevant": OTP, promotional, delivery update, bill reminder, balance
  inquiry, or anything that is NOT a completed financial transaction

CONFIDENCE:
- A float between 0 and 1 indicating how sure you are about the label

CATEGORY (choose exactly one, uppercase):

Expense categories (use only when label = "expense"):
- FOOD: restaurants, cafes, food delivery. Examples: Swiggy, Zomato,
  Starbucks, McDonald's, KFC, Domino's, local eateries.
- TRANSPORT: rides, fuel, tolls, parking, public transit. Examples: Uber,
  Ola, Rapido, petrol/diesel, IRCTC, metro, FASTag, cab fare.
- SHOPPING: retail and e-commerce (non-grocery, non-food). Examples: Amazon,
  Flipkart, Myntra, Ajio, Nykaa, Croma, malls, apparel stores.
- BILLS: recurring or utility payments. Examples: electricity, water, gas,
  mobile recharge, broadband, DTH, rent, society maintenance, insurance
  premiums, EMIs.
- ENTERTAINMENT: subscriptions, streaming, events, gaming. Examples:
  Netflix, Spotify, Hotstar, Prime Video, BookMyShow, PVR, PlayStation.
- HEALTH: medical, fitness, pharmacy. Examples: Apollo, 1mg, PharmEasy,
  hospitals, clinics, gyms, Cult.fit, diagnostics.

Income categories (use only when label = "income"):
- SALARY: monthly salary, wages, payroll, stipend.
- FREELANCE: contract or gig payments, consulting fees, Upwork, Fiverr,
  invoice payments.
- INVESTMENT: dividends, interest credited, mutual fund redemptions,
  stock proceeds, FD/PPF maturity, capital gains, SIP returns.
- GIFT: gifts received, cashback, rewards, referral bonuses, prizes.

Shared:
- OTHERS: use when none of the above categories fit clearly, or if you
  are unsure. Always use OTHERS when label = "irrelevant".

RULES:
1. If label is "irrelevant", category MUST be "OTHERS".
2. Never invent a category outside this list.
3. When unsure between two categories, prefer OTHERS over guessing.
4. Match categories to the label type: do not assign an income category
   to an expense SMS or vice versa.

SMS: {text}"""


def classify_sms(text: str) -> dict:
    try:
        client = get_client()
        response = client.models.generate_content(
            model="gemini-2.5-flash",
            contents=PROMPT_TEMPLATE.format(text=text),
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=SmsClassification,
                temperature=0,
            ),
        )
        parsed = SmsClassification.model_validate_json(response.text)
        label = parsed.label if parsed.label in ("expense", "income", "irrelevant") else "irrelevant"
        confidence = max(0.0, min(1.0, parsed.confidence))
        raw_category = parsed.category.upper()
        category = raw_category if raw_category in ALLOWED_CATEGORIES else "OTHERS"
        return {"label": label, "confidence": confidence, "category": category}


    except Exception as e:
        # Network failure, rate limit (429), malformed response, etc.
        # Fail closed — never auto-create an expense from a broken call.
        return {"label": "irrelevant", "confidence": 0.0, "category": None, "error": str(e)}