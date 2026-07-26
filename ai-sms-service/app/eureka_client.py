import socket
import py_eureka_client.eureka_client as eureka_client

import socket

def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()

async def register_with_eureka():

    hostname = socket.gethostname()
    ip = socket.gethostbyname(hostname)

    await eureka_client.init_async(
    eureka_server="http://localhost:8761/eureka/",
    app_name="ai-service",
    instance_host=get_local_ip(),
    instance_port=8000
    )