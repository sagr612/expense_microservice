import os
from google import genai
from google.genai import types
from pydantic import BaseModel

class SmsClassification(BaseModel):
    label: str        # "expense" | "income" | "irrelevant"
    confidence: float

client = genai.Client(api_key=os.environ["GEMINI_API_KEY"])

PROMPT_TEMPLATE = """You classify SMS messages for a personal finance app.

Rules:
- "expense": money was spent, debited, paid, or withdrawn
- "income": money was received, credited, or refunded
- "irrelevant": OTP, promotional, delivery, bill reminder, or anything
  that is not a completed financial transaction
- confidence is between 0 and 1, reflecting how certain you are

SMS: {text}"""

def classify_sms(text: str) -> dict:
    try:
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
        return {"label": label, "confidence": confidence}

    except Exception as e:
        # Network failure, rate limit (429), malformed response, etc.
        # Fail closed — never auto-create an expense from a broken call.
        return {"label": "irrelevant", "confidence": 0.0, "error": str(e)}