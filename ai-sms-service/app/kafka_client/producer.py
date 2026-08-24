import logging
import os
from typing import Any, Dict, Optional
import json

from kafka import KafkaAdminClient, KafkaProducer
from kafka.errors import KafkaError

logger = logging.getLogger(__name__)

# Lazy singleton — created on first use, NOT at import time.
# This way importing this module never crashes if Kafka is down.
_producer: Optional[KafkaProducer] = None


def _get_producer() -> KafkaProducer:
    global _producer
    if _producer is None:
        bootstrap_servers = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        logger.info("Initialising KafkaProducer -> %s", bootstrap_servers)
        _producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",              # wait for all in-sync replicas
            enable_idempotence=True, # dedup on the broker within a session
            retries=10,              # kafka-python default is 0; we want many
            max_in_flight_requests_per_connection=5,
            linger_ms=10,            # wait up to 10ms to batch small sends
            compression_type="gzip",
            client_id="ai-sms-service",
        )
    return _producer


def check_kafka_health() -> dict:
    """Lightweight broker ping for the /actuator/health endpoint.

    Uses KafkaAdminClient (not the send producer) so it is fast,
    read-only, and independent of the publish path.
    """
    try:
        admin = KafkaAdminClient(
            bootstrap_servers=os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092"),
            client_id="ai-sms-health",
            request_timeout_ms=3000,   # fail fast — don't hang on health checks
        )
        admin.list_topics()
        admin.close()
        return {"status": "UP"}
    except Exception as e:
        return {"status": "DOWN", "error": str(e)}


def publish_expense_event(event: Dict[str, Any]) -> bool:
    """Publish to Kafka. Returns True on confirmed delivery, False on failure.

    We block on the Future so the caller can honestly tell the user whether
    the expense was accepted for asynchronous processing.
    """
    try:
        future = _get_producer().send(
            "expense.create.request",
            key=event["userId"],
            value=event,
        )
        # Block up to 15s for broker acknowledgement. On timeout or broker
        # error, this raises KafkaError (or a subclass).
        record_metadata = future.get(timeout=15)
        logger.info(
            "Published expense event topic=%s partition=%s offset=%s externalId=%s",
            record_metadata.topic,
            record_metadata.partition,
            record_metadata.offset,
            event.get("externalId"),
        )
        return True
    except KafkaError as e:
        logger.error(
            "Failed to publish expense event externalId=%s: %s",
            event.get("externalId"),
            e,
        )
        return False
    except Exception as e:
        logger.exception(
            "Unexpected error publishing expense event externalId=%s: %s",
            event.get("externalId"),
            e,
        )
        return False
