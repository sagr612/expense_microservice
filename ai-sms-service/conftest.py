"""Pytest configuration: ensures the project root is on sys.path so `app` is importable."""
import sys
from pathlib import Path
from unittest.mock import patch

import pytest

PROJECT_ROOT = Path(__file__).resolve().parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))


@pytest.fixture(autouse=True)
def _no_kafka_lifecycle():
    """The app's lifespan must not open real Kafka connections or contact Eureka during tests."""
    with patch("app.main.close_producer"), \
            patch("app.main.register_with_eureka"), \
            patch("py_eureka_client.eureka_client.stop_async"):
        yield