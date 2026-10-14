import asyncio
import contextvars
import hashlib
import os
from datetime import date
from fastapi import FastAPI, Depends
from dotenv import load_dotenv
from fastapi.exceptions import RequestValidationError
from prometheus_fastapi_instrumentator import Instrumentator

load_dotenv()

from app.auth import get_current_user_id
from app.classifier.gemini_classifier import async_classify_sms
from app.extractors.regex_extractor import extract_amount, extract_merchant, guess_category
from app.kafka_client.producer import publish_expense_event, check_kafka_health, close_producer
from app.models.schemas import SmsClassifyRequest, SmsClassifyResponse, SmsExpenseEvent

from contextlib import asynccontextmanager

from app.eureka_client import register_with_eureka
from app.tracing import setup_tracing
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from app.logging_config import configure_logging
import logging
from fastapi import Request
from fastapi.responses import JSONResponse
from datetime import datetime, timezone

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    try:
        await register_with_eureka()
    except Exception as e:
        logger.warning("Eureka registration failed; continuing without it: %s", e)

    yield  # app is running and serving requests here

    # ── SHUTDOWN (runs on SIGTERM / uvicorn stop) ─────────────────────────
    # 1. Flush and close the Kafka producer so no in-flight batches are lost.
    try:
        close_producer()
    except Exception as e:
        logger.warning("Error closing Kafka producer: %s", e)

    # 2. Deregister from Eureka so the gateway stops routing immediately.
    #    Without this the instance stays in the registry for ~90s (lease expiry).
    try:
        import py_eureka_client.eureka_client as eureka_client
        await eureka_client.stop_async()
        logger.info("Deregistered from Eureka")
    except Exception as e:
        logger.warning("Error deregistering from Eureka: %s", e)


app = FastAPI(
    title="ai-service",
    lifespan=lifespan
)

setup_tracing()
configure_logging()
FastAPIInstrumentor.instrument_app(app)
Instrumentator().instrument(app).expose(app)

CONFIDENCE_THRESHOLD = 0.85


@app.post("/api/v1/sms/classify", response_model=SmsClassifyResponse)
async def classify(payload: SmsClassifyRequest, x_user_id: str = Depends(get_current_user_id)):
    cleaned_text = payload.smsText.strip()
    # Async Gemini call — event loop is free to serve other requests while waiting
    result = await async_classify_sms(cleaned_text)
    label, confidence = result["label"], result["confidence"]
    is_degraded = result.get("error") is not None

    if label == "irrelevant":
        return SmsClassifyResponse(
            label=label, confidence=confidence, autoCreated=False, degraded=is_degraded)

    amount = extract_amount(cleaned_text)
    if amount is None:
        return SmsClassifyResponse(label=label, confidence=confidence, autoCreated=False, degraded=is_degraded)

    merchant = extract_merchant(cleaned_text)
    llm_category = result.get("category")
    if llm_category and llm_category != "OTHERS":
        category = llm_category
    else:
        category = guess_category(cleaned_text)
    external_id = "sms-" + hashlib.sha256(
        (x_user_id + cleaned_text).encode()
    ).hexdigest()[:16]

    event = SmsExpenseEvent(
        externalId=external_id,
        userId=x_user_id,
        amount=amount,
        currency="INR",  # TODO: multi-currency not yet supported
        type=label.upper(),
        category=category,
        title=merchant or "SMS detected transaction",
        note="Auto-detected from SMS",
        transactionDate=str(date.today()),
    )

    auto_created = False
    if confidence >= CONFIDENCE_THRESHOLD:
        try:
            # kafka-python's .send().get() is blocking — run it in a threadpool
            # so it doesn't freeze the async event loop.
            loop = asyncio.get_running_loop()
            ctx = contextvars.copy_context()
            published = await loop.run_in_executor(
                None, ctx.run, publish_expense_event, event.model_dump()
            )
            auto_created = published
            if not published:
                logger.warning(
                    "Confidence %.2f above threshold but Kafka publish failed; "
                    "client will see autoCreated=false for externalId=%s",
                    confidence,
                    external_id,
                )
        except Exception as e:
            logger.exception(
                "Unexpected error publishing expense event externalId=%s: %s",
                external_id,
                e,
            )

    logger.info(
        "SMS classified label=%s confidence=%.2f autoCreated=%s",
        label,
        confidence,
        auto_created,
    )

    return SmsClassifyResponse(
        label=label, confidence=confidence, autoCreated=auto_created, event=event, degraded=is_degraded
    )


@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    return JSONResponse(
        status_code=422,
        content={
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "status": 422,
            "error": "Unprocessable Entity",
            "message": "Invalid or missing request body fields",
            "details": exc.errors()
        }
    )


@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.exception("Unhandled error on %s", request.url.path)
    return JSONResponse(
        status_code=500,
        content={
            "timestamp": datetime.now(timezone.utc).isoformat(),
            "status": 500,
            "error": "Internal Server Error",
            "message": "An unexpected error occurred",
        },
    )


@app.get("/actuator/health/readiness")
@app.get("/actuator/health")
def health():
    checks = {}
    overall = "UP"

    # --- 1. Kafka broker reachability ---
    kafka_result = check_kafka_health()
    checks["kafka"] = kafka_result
    if kafka_result["status"] == "DOWN":
        overall = "DOWN"

    # --- 2. Gemini API key present ---
    if os.environ.get("GEMINI_API_KEY"):
        checks["gemini"] = {"status": "UP"}
    else:
        checks["gemini"] = {"status": "DOWN", "error": "GEMINI_API_KEY not set"}
        overall = "DOWN"

    # --- 3. JWT secret present ---
    if os.environ.get("JWT_SECRET"):
        checks["jwt"] = {"status": "UP"}
    else:
        checks["jwt"] = {"status": "DOWN", "error": "JWT_SECRET not set"}
        overall = "DOWN"

    status_code = 200 if overall == "UP" else 503
    return JSONResponse(
        status_code=status_code,
        content={"status": overall, "components": checks},
    )


@app.get("/actuator/health/liveness")
def liveness():
    """Process liveness probe — returns 200 immediately without touching dependencies."""
    return {"status": "UP"}
