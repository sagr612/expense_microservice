# tests/test_schemas.py
"""Unit tests for Pydantic request/response schemas."""
import pytest
from pydantic import ValidationError

from app.models.schemas import SmsClassifyRequest, SmsClassifyResponse, SmsExpenseEvent


# ---------------------------------------------------------------------------
# SmsClassifyRequest
# ---------------------------------------------------------------------------

def test_sms_classify_request_valid():
    req = SmsClassifyRequest(smsText="Rs. 299 debited")
    assert req.smsText == "Rs. 299 debited"


def test_sms_classify_request_missing_field():
    with pytest.raises(ValidationError):
        SmsClassifyRequest()


def test_sms_classify_request_empty_string():
    req = SmsClassifyRequest(smsText="")
    assert req.smsText == ""


def test_sms_classify_request_wrong_type():
    with pytest.raises(ValidationError):
        SmsClassifyRequest(smsText=12345)


# ---------------------------------------------------------------------------
# SmsExpenseEvent
# ---------------------------------------------------------------------------

def test_sms_expense_event_valid_with_defaults():
    event = SmsExpenseEvent(
        externalId="sms-abc",
        userId="user-1",
        amount=299.0,
        type="EXPENSE",
        category="FOOD",
        title="Swiggy",
        note="Auto-detected from SMS",
        transactionDate="2026-08-05",
    )
    assert event.currency == "INR"  # default
    assert event.amount == 299.0


def test_sms_expense_event_missing_required_field():
    with pytest.raises(ValidationError):
        SmsExpenseEvent(
            userId="user-1",
            amount=299.0,
            type="EXPENSE",
            category="FOOD",
            title="Swiggy",
            note="Auto-detected from SMS",
            transactionDate="2026-08-05",
        )  # missing externalId


def test_sms_expense_event_negative_amount():
    """Negative amounts are technically allowed by the schema (no constraint)."""
    event = SmsExpenseEvent(
        externalId="sms-abc",
        userId="user-1",
        amount=-50.0,
        type="EXPENSE",
        category="FOOD",
        title="Refund",
        note="Auto-detected from SMS",
        transactionDate="2026-08-05",
    )
    assert event.amount == -50.0


def test_sms_expense_event_custom_currency():
    event = SmsExpenseEvent(
        externalId="sms-abc",
        userId="user-1",
        amount=100.0,
        currency="USD",
        type="INCOME",
        category="OTHERS",
        title="Salary",
        note="Auto-detected from SMS",
        transactionDate="2026-08-05",
    )
    assert event.currency == "USD"


# ---------------------------------------------------------------------------
# SmsClassifyResponse
# ---------------------------------------------------------------------------

def test_sms_classify_response_without_event():
    resp = SmsClassifyResponse(label="irrelevant", confidence=0.95, autoCreated=False)
    assert resp.event is None


def test_sms_classify_response_with_event():
    event = SmsExpenseEvent(
        externalId="sms-abc",
        userId="user-1",
        amount=299.0,
        type="EXPENSE",
        category="FOOD",
        title="Swiggy",
        note="Auto-detected from SMS",
        transactionDate="2026-08-05",
    )
    resp = SmsClassifyResponse(label="expense", confidence=0.99, autoCreated=True, event=event)
    assert resp.event is not None
    assert resp.event.amount == 299.0


def test_sms_classify_response_missing_required_field():
    with pytest.raises(ValidationError):
        SmsClassifyResponse(label="expense", confidence=0.99)  # missing autoCreated