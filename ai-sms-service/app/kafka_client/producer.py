import logging
import os
import time
from typing import Any, Dict, Optional
import json

from kafka import KafkaAdminClient, KafkaProducer
from kafka.errors import KafkaError
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator

logger = logging.getLogger(__name__)

_producer: Optional[KafkaProducer] = None
_admin: Optional[KafkaAdminClient] = None
_health_cache: dict = {"ts": 0.0, "result": None}
_HEALTH_TTL_SECONDS = 5.0


def _get_producer() -> KafkaProducer:
    global _producer
    if _producer is None:
        bootstrap_servers = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
        logger.info("Initialising KafkaProducer -> %s", bootstrap_servers)
        _producer = KafkaProducer(
            bootstrap_servers=bootstrap_servers,
            value_serializer=lambda v: json.dumps(v).encode("utf-8"),
            key_serializer=lambda k: k.encode("utf-8") if k else None,
            acks="all",  # wait for all in-sync replicas
            enable_idempotence=True,  # dedup on the broker within a session
            retries=10,  # kafka-python default is 0; we want many
            max_in_flight_requests_per_connection=5,
            linger_ms=10,  # wait up to 10ms to batch small sends
            compression_type="gzip",
            client_id="ai-sms-service",
        )
    return _producer


def close_producer(flush_timeout: float = 10.0, close_timeout: float = 5.0) -> None:
    """Close the producer only if it was ever created. Always safe to call."""
    global _producer
    if _producer is None:
        logger.info("Kafka producer was never created; nothing to close")
        return

    try:
        _producer.flush(timeout=flush_timeout)
    except Exception as e:
        logger.warning("Kafka flush failed during shutdown: %s", e)
    finally:
        try:
            _producer.close(timeout=close_timeout)
        except Exception as e:
            logger.warning("Kafka close failed during shutdown: %s", e)
        finally:
            _producer = None
            logger.info("Kafka producer closed gracefully")


def check_kafka_health() -> dict:
    """Fast, cached broker liveness check. Reuses admin connection and never hangs."""
    now = time.monotonic()
    if _health_cache["result"] is not None and (now - _health_cache["ts"]) < _HEALTH_TTL_SECONDS:
        return _health_cache["result"]
    result = _check_kakfa_health_uncached()
    _health_cache.update(ts=now, result=result)
    return result


def _check_kakfa_health_uncached() -> dict:
    global _admin
    bootstrap_servers = os.environ.get("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    try:
        if _admin is None:
            _admin = KafkaAdminClient(
                bootstrap_servers=bootstrap_servers,
                client_id="ai-sms-health",
                request_timeout=3000,
                bootstrap_timeout_ms=3000,
            )

        _admin.list_topics()
        return {"status": "UP"}
    except Exception as e:
        if _admin is not None:
            try:
                _admin.close()
            except Exception:
                pass
        _admin = None
        return {"status": "DOWN", "error": str(e)}


def publish_expense_event(event: Dict[str, Any]) -> bool:
    """Publish to Kafka. Returns True on confirmed delivery, False on failure.

    We block on the Future so the caller can honestly tell the user whether
    the expense was accepted for asynchronous processing.
    """
    try:
        carrier = {}
        TraceContextTextMapPropagator().inject(carrier)
        kafka_headers = [(k, v.encode("utf-8")) for (k, v) in carrier.items()]

        if "traceparent" in carrier:
            parts = carrier["traceparent"].split("-")
            if len(parts) >= 3:
                b3_val = f"{parts[1]}-{parts[2]}-1"
                kafka_headers.append(("b3", b3_val.encode("utf-8")))

        future = _get_producer().send(
            "expense.create.request",
            key=event["userId"],
            value=event,
            headers=kafka_headers,
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
