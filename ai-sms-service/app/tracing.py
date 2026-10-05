import os
from opentelemetry import trace
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.zipkin.json import ZipkinExporter


def setup_tracing():
    resource = Resource.create({"service.name": "ai-service"})
    # print("RESOURCE ATTRIBUTES:", resource.attributes)
    provider = TracerProvider(resource=resource)
    zipkin_endpoint = os.getenv("ZIPKIN_ENDPOINT","http://localhost:9411/api/v2/spans")
    zipkin_exporter = ZipkinExporter(endpoint=zipkin_endpoint)
    provider.add_span_processor(BatchSpanProcessor(zipkin_exporter))
    trace.set_tracer_provider(provider)
