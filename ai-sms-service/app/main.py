# app/main.py
import hashlib
import logging
import os
from datetime import date
from fastapi import FastAPI, Header
from dotenv import load_dotenv
from opentelemetry import trace
from prometheus_fastapi_instrumentator import Instrumentator

load_dotenv()
os.makedirs("logs", exist_ok=True)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(threadName)s] %(levelname)s [ai-service] %(name)s : %(message)s",
    handlers=[
        logging.StreamHandler(),                          # stdout (keep this)
        logging.FileHandler("logs/ai-service.log"),       # file (add this)
    ]
)
from app.classifier.gemini_classifier import classify_sms
from app.extractors.regex_extractor import extract_amount, extract_merchant, guess_category
from app.kafka_client.producer import publish_expense_event
from app.models.schemas import SmsClassifyRequest, SmsClassifyResponse, SmsExpenseEvent

from contextlib import asynccontextmanager

from app.eureka_client import register_with_eureka
from app.tracing import setup_tracing
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from app.logging_config import configure_logging
import logging

logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    await register_with_eureka()
    yield

app = FastAPI(
    title="ai-service",
    lifespan=lifespan
)

setup_tracing()
configure_logging()
FastAPIInstrumentor.instrument_app(app)
Instrumentator().instrument(app).expose(app)

# app = FastAPI(title="ai-service")

CONFIDENCE_THRESHOLD = 0.85
# span = trace.get_current_span()

# ctx = span.get_span_context()


@app.post("/api/v1/sms/classify", response_model=SmsClassifyResponse)
def classify(payload: SmsClassifyRequest, x_user_id: str = Header(...)):
    result = classify_sms(payload.smsText)
    label, confidence = result["label"], result["confidence"]
    # span = trace.get_current_span()

    # ctx = span.get_span_context()

   
    if label == "irrelevant":
        return SmsClassifyResponse(label=label, confidence=confidence, autoCreated=False)

    amount = extract_amount(payload.smsText)
    if amount is None:
        # Nothing to build an event from — no point faking one
        return SmsClassifyResponse(label=label, confidence=confidence, autoCreated=False)

    merchant = extract_merchant(payload.smsText)
    llm_category = result.get("category")
    if llm_category and llm_category != "OTHERS":
        category = llm_category
    else:
        category = guess_category(payload.smsText)
    external_id = "sms-" + hashlib.sha256(
        (x_user_id + payload.smsText).encode()
    ).hexdigest()[:16]

    event = SmsExpenseEvent(
        externalId=external_id,
        userId=x_user_id,
        amount=amount,
        currency="INR",
        type=label.upper(),
        category=category,
        title=merchant or "SMS detected transaction",
        note="Auto-detected from SMS",
        transactionDate=str(date.today()),
    )

    auto_created = False
    if confidence >= CONFIDENCE_THRESHOLD:
        publish_expense_event(event.model_dump())
        auto_created = True
    logger.info(
        "SMS classified label=%s confidence=%.2f",
        label,
        confidence
    )
    return SmsClassifyResponse(
        label=label, confidence=confidence, autoCreated=auto_created, event=event,
    )

@app.get("/actuator/health")
def health():
    return {"status": "UP"}