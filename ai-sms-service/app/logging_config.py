import logging

from opentelemetry import trace


class TraceContextFilter(logging.Filter):
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
    handler = logging.StreamHandler()
    formatter = logging.Formatter(
        "%(asctime)s %(levelname)s "
        "[%(trace_id)s-%(span_id)s] "
        "%(name)s - %(message)s"
    )
    handler.setFormatter(formatter)
    handler.addFilter(TraceContextFilter() )

    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(logging.INFO)