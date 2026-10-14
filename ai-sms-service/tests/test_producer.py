# tests/test_producer.py
from unittest.mock import MagicMock, patch, ANY
from kafka.errors import KafkaTimeoutError
from app.kafka_client.producer import publish_expense_event


def _make_success_future(topic="expense.create.request", partition=0, offset=42):
    """Build a Future-like mock whose .get() returns RecordMetadata-like data."""
    metadata = MagicMock()
    metadata.topic = topic
    metadata.partition = partition
    metadata.offset = offset

    future = MagicMock()
    future.get.return_value = metadata
    return future


@patch("app.kafka_client.producer._get_producer")
def test_publish_expense_event_returns_true_on_success(mock_get_producer):
    mock_producer = MagicMock()
    mock_producer.send.return_value = _make_success_future()
    mock_get_producer.return_value = mock_producer

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

    result = publish_expense_event(event)

    assert result is True
    mock_producer.send.assert_called_once_with(
        "expense.create.request",
        key="user-42",
        value=event,
        headers =ANY
    )
    # future.get should have been called with a positive timeout
    mock_producer.send.return_value.get.assert_called_once()
    _, kwargs = mock_producer.send.return_value.get.call_args
    assert kwargs.get("timeout", 0) > 0


@patch("app.kafka_client.producer._get_producer")
def test_publish_expense_event_returns_false_on_kafka_error(mock_get_producer):
    """Broker/timeout errors must not lie to the caller."""
    mock_producer = MagicMock()
    future = MagicMock()
    future.get.side_effect = KafkaTimeoutError("broker unresponsive")
    mock_producer.send.return_value = future
    mock_get_producer.return_value = mock_producer

    result = publish_expense_event({"userId": "user-7", "amount": 100.0})

    assert result is False


@patch("app.kafka_client.producer._get_producer")
def test_publish_expense_event_returns_false_on_unexpected_exception(mock_get_producer):
    """Any unexpected exception must be swallowed, logged, and reported as failure."""
    mock_producer = MagicMock()
    future = MagicMock()
    future.get.side_effect = ValueError("something weird")
    mock_producer.send.return_value = future
    mock_get_producer.return_value = mock_producer

    result = publish_expense_event({"userId": "user-9", "amount": 50.0})

    assert result is False


@patch("app.kafka_client.producer._get_producer")
def test_publish_expense_event_uses_userid_as_key(mock_get_producer):
    """Message key partitions events per user — critical for ordering guarantees."""
    mock_producer = MagicMock()
    mock_producer.send.return_value = _make_success_future()
    mock_get_producer.return_value = mock_producer

    publish_expense_event({"userId": "user-7", "amount": 100.0})

    call = mock_producer.send.call_args
    assert call.kwargs["key"] == "user-7"
