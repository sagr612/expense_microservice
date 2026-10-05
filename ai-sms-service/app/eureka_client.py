import os
import socket
from typing import override

import py_eureka_client.eureka_client as eureka_client


def get_local_ip():
    override = os.getenv("EUREKA_INSTANCE_HOST")
    if override:
        return override
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return socket.gethostbyname(socket.gethostname())
    finally:
        s.close()


async def register_with_eureka():
    await eureka_client.init_async(
        eureka_server=os.getenv("EUREKA_URI", "http://localhost:8761/eureka/"),
        app_name="ai-service",
        instance_host=get_local_ip(),
        instance_port=int(os.getenv("SERVICE_PORT", "8000")),
    )
