# tests/test_main_api.py
"""API endpoint tests for the ai-sms-service FastAPI app."""
from unittest.mock import patch

import pytest

# --- Module-level mocks so importing app.main does not attempt real connections ---
# Patch the source modules BEFORE app.main is imported, otherwise the real
# setup_tracing()/instrument_app()/Instrumentator() run during import and
# spawn background threads that try to reach Zipkin/Prometheus.
with patch("kafka.KafkaProducer"), \
     patch("app.tracing.setup_tracing"), \
     patch("opentelemetry.instrumentation.fastapi.FastAPIInstrumentor.instrument_app"), \
     patch("prometheus_fastapi_instrumentator.Instrumentator"):
    from app.main import app

from fastapi.testclient import TestClient


@pytest.fixture()
def client():
    """Provide a TestClient with Eureka registration stubbed out."""
    with patch("app.main.register_with_eureka"):
        with TestClient(app) as test_client:
            yield test_client


# ---------------------------------------------------------------------------
# Health endpoint
# ---------------------------------------------------------------------------

def test_health_returns_up(client):
    response = client.get("/actuator/health")
    assert response.status_code == 200
    assert response.json() == {"status": "UP"}


# ---------------------------------------------------------------------------
# POST /api/v1/sms/classify
# ---------------------------------------------------------------------------

def test_classify_irrelevant_returns_auto_created_false(client):
    with patch("app.main.classify_sms", return_value={"label": "irrelevant", "confidence": 0.95}):
        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Your OTP is 123456"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "irrelevant"
    assert body["confidence"] == 0.95
    assert body["autoCreated"] is False
    assert body["event"] is None


def test_classify_expense_no_amount_returns_auto_created_false(client):
    with patch("app.main.classify_sms", return_value={"label": "expense", "confidence": 0.99, "category": "OTHERS"}), \
         patch("app.main.extract_amount", return_value=None):
        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Payment received"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "expense"
    assert body["autoCreated"] is False
    assert body["event"] is None


def test_classify_expense_below_threshold_no_kafka_publish(client):
    with patch("app.main.classify_sms", return_value={"label": "expense", "confidence": 0.80, "category": "FOOD"}), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Swiggy"), \
         patch("app.main.guess_category", return_value="BILLS"), \
         patch("app.main.publish_expense_event") as mock_publish:

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 spent at Swiggy"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "expense"
    assert body["autoCreated"] is False
    assert body["event"] is not None
    assert body["event"]["amount"] == 299.0
    assert body["event"]["category"] == "FOOD"  # ← because LLM returned "OTHERS" and regex fallback was used
    mock_publish.assert_not_called()


def test_classify_expense_above_threshold_publishes_kafka(client):
    with patch("app.main.classify_sms", return_value={"label": "expense", "confidence": 0.95, "category": "FOOD"}), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Swiggy"), \
         patch("app.main.guess_category", return_value="FOOD"), \
         patch("app.main.publish_expense_event") as mock_publish:

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 debited at Swiggy VPA swiggy@ybl"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "expense"
    assert body["autoCreated"] is True
    assert body["event"] is not None
    assert body["event"]["userId"] == "user-123"
    assert body["event"]["amount"] == 299.0
    assert body["event"]["currency"] == "INR"
    assert body["event"]["type"] == "EXPENSE"
    assert body["event"]["category"] == "FOOD"
    assert body["event"]["title"] == "Swiggy"
    assert body["event"]["note"] == "Auto-detected from SMS"
    assert body["event"]["externalId"].startswith("sms-")
    assert len(body["event"]["externalId"]) == 4 + 16  # "sms-" + 16 hex chars
    mock_publish.assert_called_once()


def test_classify_income_above_threshold_publishes_kafka(client):
    with patch("app.main.classify_sms", return_value={"label": "income", "confidence": 0.97, "category": "OTHERS"}), \
         patch("app.main.extract_amount", return_value=5000.0), \
         patch("app.main.extract_merchant", return_value=None), \
         patch("app.main.guess_category", return_value="OTHERS"), \
         patch("app.main.publish_expense_event") as mock_publish:

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 5000 credited to your account"},
            headers={"x-user-id": "user-456"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["label"] == "income"
    assert body["autoCreated"] is True
    assert body["event"]["type"] == "INCOME"
    assert body["event"]["title"] == "SMS detected transaction"  # merchant is None -> fallback title
    assert body["event"]["userId"] == "user-456"


def test_classify_external_id_is_deterministic(client):
    """Same user + same SMS must produce the same externalId."""
    with patch("app.main.classify_sms", return_value={"label": "expense", "confidence": 0.99, "category": "OTHERS"}), \
         patch("app.main.extract_amount", return_value=100.0), \
         patch("app.main.extract_merchant", return_value=None), \
         patch("app.main.guess_category", return_value="OTHERS"), \
         patch("app.main.publish_expense_event"):

        payload = {"smsText": "Rs. 100 debited from HDFC Bank"}

        r1 = client.post("/api/v1/sms/classify", json=payload, headers={"x-user-id": "user-1"}).json()
        r2 = client.post("/api/v1/sms/classify", json=payload, headers={"x-user-id": "user-1"}).json()

    assert r1["event"]["externalId"] == r2["event"]["externalId"]


def test_classify_external_id_differs_by_user(client):
    """Different users submitting the same SMS must get different externalIds."""
    with patch("app.main.classify_sms", return_value={"label": "expense", "confidence": 0.99, "category": "OTHERS"}), \
         patch("app.main.extract_amount", return_value=100.0), \
         patch("app.main.extract_merchant", return_value=None), \
         patch("app.main.guess_category", return_value="OTHERS"), \
         patch("app.main.publish_expense_event"):

        payload = {"smsText": "Rs. 100 debited from HDFC Bank"}

        r1 = client.post("/api/v1/sms/classify", json=payload, headers={"x-user-id": "user-1"}).json()
        r2 = client.post("/api/v1/sms/classify", json=payload, headers={"x-user-id": "user-2"}).json()

    assert r1["event"]["externalId"] != r2["event"]["externalId"]


def test_classify_missing_required_header_returns_422(client):
    with patch("app.main.classify_sms", return_value={"label": "irrelevant", "confidence": 0.9}):
        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Hello"},
        )

    assert response.status_code == 422


def test_classify_invalid_payload_missing_sms_text(client):
    response = client.post(
        "/api/v1/sms/classify",
        json={},
        headers={"x-user-id": "user-123"},
    )

    assert response.status_code == 422


def test_classify_invalid_payload_wrong_type(client):
    response = client.post(
        "/api/v1/sms/classify",
        json={"smsText": 12345},
        headers={"x-user-id": "user-123"},
    )

    assert response.status_code == 422

def test_classify_uses_llm_category_when_provided(client):
    """When LLM returns a valid category (not OTHERS), it should be used."""
    with patch("app.main.classify_sms", return_value={
             "label": "expense", "confidence": 0.95, "category": "FOOD"
         }), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Starbucks"), \
         patch("app.main.guess_category") as mock_guess_category, \
         patch("app.main.publish_expense_event"):

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 at Starbucks"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["event"]["category"] == "FOOD"
    mock_guess_category.assert_not_called()   # ← key assertion: fallback NOT used


def test_classify_falls_back_to_regex_when_llm_returns_others(client):
    """When LLM returns 'OTHERS', regex fallback should run."""
    with patch("app.main.classify_sms", return_value={
             "label": "expense", "confidence": 0.95, "category": "OTHERS"
         }), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Swiggy"), \
         patch("app.main.guess_category", return_value="FOOD") as mock_guess, \
         patch("app.main.publish_expense_event"):

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 at Swiggy"},
            headers={"x-user-id": "user-123"},
        )

    assert response.status_code == 200
    body = response.json()
    assert body["event"]["category"] == "FOOD"
    mock_guess.assert_called_once()   # ← key assertion: fallback used


def test_classify_falls_back_to_regex_when_llm_category_none(client):
    """When LLM fails (category=None), regex fallback should run."""
    with patch("app.main.classify_sms", return_value={
             "label": "expense", "confidence": 0.95, "category": None
         }), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Swiggy"), \
         patch("app.main.guess_category", return_value="FOOD") as mock_guess, \
         patch("app.main.publish_expense_event"):

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 at Swiggy"},
            headers={"x-user-id": "user-123"},
        )

    body = response.json()
    assert body["event"]["category"] == "FOOD"
    mock_guess.assert_called_once()


def test_classify_missing_category_key_falls_back_to_regex(client):
    """Backward-compat: if classify_sms doesn't return a 'category' key at all."""
    with patch("app.main.classify_sms", return_value={
             "label": "expense", "confidence": 0.95
             # ← no category key
         }), \
         patch("app.main.extract_amount", return_value=299.0), \
         patch("app.main.extract_merchant", return_value="Swiggy"), \
         patch("app.main.guess_category", return_value="FOOD") as mock_guess, \
         patch("app.main.publish_expense_event"):

        response = client.post(
            "/api/v1/sms/classify",
            json={"smsText": "Rs. 299 at Swiggy"},
            headers={"x-user-id": "user-123"},
        )

    body = response.json()
    assert body["event"]["category"] == "FOOD"
    mock_guess.assert_called_once()
