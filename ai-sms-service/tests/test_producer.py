# tests/test_producer.py
"""Unit tests for the Kafka producer."""
from unittest.mock import MagicMock, patch

# Patch KafkaProducer before importing the module so the module-level
# `producer = KafkaProducer(...)` call doesn't attempt a real connection.
with patch("kafka.KafkaProducer"):
    from app.kafka_client.producer import publish_expense_event


@patch("app.kafka_client.producer.producer")
def test_publish_expense_event_sends_with_user_key(mock_producer):
    """publish_expense_event should send to the topic with userId as key."""
    event = {
        "externalId": "sms-abc123",
        "userId": "user-42",
        "amount": 299.0,
        "currency": "INR",
        "type": "EXPENSE",
        "category": "FOOD",
        "title": "Swiggy",
        "note": "Auto-detected from SMS",
        "transactionDate": "2026-08-05",
    }

    publish_expense_event(event)

    mock_producer.send.assert_called_once_with(
        "expense.create.request",
        key="user-42",
        value=event,
    )
    mock_producer.flush.assert_called_once()


@patch("app.kafka_client.producer.producer")
def test_publish_expense_event_sends_different_user(mock_producer):
    """Ensure the key changes with different userIds."""
    publish_expense_event({"userId": "user-7", "amount": 100.0})

    mock_producer.send.assert_called_once_with(
        "expense.create.request",
        key="user-7",
        value={"userId": "user-7", "amount": 100.0},
    )
    mock_producer.flush.assert_called_once()