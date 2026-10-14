import logging
import os
from google import genai
from google.genai import types, errors
from prometheus_client import Counter
from pydantic import BaseModel
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception

from typing import Literal, get_args

logger = logging.getLogger(__name__)

GEMINI_TIMEOUT_MS = int(os.getenv("GEMINI_TIMEOUT_MS", "10000"))
GEMINI_MODEL = os.getenv("GEMINI_MODEL", "gemini-2.5-flash")

LLM_FAILURES = Counter("ai_sms_llm_failures_total", "Gemini classification failures", ["reason"])

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


_client = None


def get_client():
    global _client
    if _client is None:
        _client = genai.Client(api_key=os.environ["GEMINI_API_KEY"],
                               http_options=types.HttpOptions(timeout=GEMINI_TIMEOUT_MS))
    return _client


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


def _parse_response(response) -> dict:
    parsed = SmsClassification.model_validate_json(response.text)
    label = parsed.label if parsed.label in ("expense", "income", "irrelevant") else "irrelevant"
    confidence = max(0.0, min(1.0, parsed.confidence))
    raw_category = parsed.category.upper()
    category = raw_category if raw_category in ALLOWED_CATEGORIES else "OTHERS"
    return {"label": label, "confidence": confidence, "category": category}


def _timeout_result() -> dict:
    logger.warning("Gemini call timed out after %dms", GEMINI_TIMEOUT_MS)
    LLM_FAILURES.labels(reason="timeout").inc()
    return {"label": "irrelevant", "confidence": 0.0, "category": None,
            "error": f"Gemini timeout after {GEMINI_TIMEOUT_MS}ms"}


def _error_result(e: Exception) -> dict:
    logger.exception("Gemini classify_sms failed: %s", e)
    LLM_FAILURES.labels(reason="error").inc()
    return {"label": "irrelevant", "confidence": 0.0, "category": None, "error": str(e)}


def is_retryable_error(exception: BaseException) -> bool:
    """Only retry transient errors: Timeout, 429 (Rate limit), 503/500 (Server errors)."""
    if isinstance(exception, (TimeoutError, errors.ServerError)):
        return True
    if isinstance(exception, errors.APIError) and exception.code in (429, 500, 503, 504):
        return True
    return False


# ── Sync version (kept for tests / backwards compat) ──────────────────────────
def classify_sms(text: str) -> dict:
    try:
        client = get_client()
        response = client.models.generate_content(
            model=GEMINI_MODEL,
            contents=PROMPT_TEMPLATE.format(text=text),
            config=types.GenerateContentConfig(
                response_mime_type="application/json",
                response_schema=SmsClassification,
                temperature=0,
            ),
        )
        return _parse_response(response)
    except TimeoutError as e:
        return _timeout_result()
    except Exception as e:
        return _error_result(e)


# ── Async version (used by the FastAPI endpoint) ───────────────────────────────
# Uses client.aio.models.generate_content — native async, no threadpool needed.
# The event loop is free to handle other requests while waiting for Gemini.
@retry(
    stop=stop_after_attempt(2),
    wait=wait_exponential(multiplier=1, min=1, max=3),
    retry=retry_if_exception(is_retryable_error),
    reraise=True,
)
async def _call_gemini_with_retry(client, text: str):
    return await client.aio.models.generate_content(
        model=GEMINI_MODEL,
        contents=PROMPT_TEMPLATE.format(text=text),
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=SmsClassification,
            temperature=0,  # randomness
        ),
    )


async def async_classify_sms(text: str) -> dict:
    try:
        client = get_client()
        response = await _call_gemini_with_retry(client, text)
        return _parse_response(response)
    except TimeoutError as e:
        return _timeout_result()
    except Exception as e:
        return _error_result(e)
