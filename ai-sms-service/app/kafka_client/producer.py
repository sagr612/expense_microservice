import json
from kafka import KafkaProducer

producer = KafkaProducer(
    bootstrap_servers="localhost:9092",
    value_serializer=lambda v: json.dumps(v).encode("utf-8"),
    key_serializer=lambda k: k.encode("utf-8") if k else None,
)

def publish_expense_event(event: dict):
    producer.send("expense.create.request", key=event["userId"], value=event)
    producer.flush()