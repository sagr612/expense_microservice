import logging
import os

from opentelemetry import trace


class TraceContextFilter(logging.Filter):
    """Injects OpenTelemetry trace_id/span_id into every log record."""
    def filter(self, record):
        span = trace.get_current_span()
        ctx = span.get_span_context()

        if ctx.is_valid:
            record.trace_id = f"{ctx.trace_id:032x}"
            record.span_id = f"{ctx.span_id:016x}"
        else:
            record.trace_id = "-"
            record.span_id = "-"
        return True


def configure_logging():
    """Single source of truth for logging.

    Emits identical formatted lines to:
      - stdout (picked up by Docker/kubectl logs)
      - logs/ai-service.log (picked up by Promtail -> Loki)
    """
    os.makedirs("logs", exist_ok=True)

    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s [ai-service] "
        "[%(trace_id)s-%(span_id)s] "
        "%(name)s - %(message)s"
    )
    trace_filter = TraceContextFilter()

    stream_handler = logging.StreamHandler()
    stream_handler.setFormatter(formatter)
    stream_handler.addFilter(trace_filter)

    file_handler = logging.FileHandler("logs/ai-service.log")
    file_handler.setFormatter(formatter)
    file_handler.addFilter(trace_filter)

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(stream_handler)
    root.addHandler(file_handler)
    root.setLevel(logging.INFO)
