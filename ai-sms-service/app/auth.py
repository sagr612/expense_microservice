import base64, os, jwt
from typing import Optional

from fastapi import Header, HTTPException


def _get_jwt_secret() -> bytes:
    """
    Decodes the base64 JWT secret.
    Cross-service contract with AuthService1
    (JwtService.java: Decoders.BASE64.decode(secret)).
    """
    secret = os.environ.get("JWT_SECRET")
    if not secret:
        raise HTTPException(status_code=500, detail="JWT_SECRET is not configured")
    try:
        return base64.b64decode(secret)
    except Exception:
        raise HTTPException(status_code=500, detail="JWT_SECRET is not valid base64")


def get_current_user_id(authorization: Optional[str] = Header(default=None)) -> str:
    if not authorization:
        raise HTTPException(status_code=401, detail="Missing Authorization header")
    if not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Malformed Authorization header")
    token = authorization[7:].strip()

    secret = _get_jwt_secret()
    try:
        payload = jwt.decode(token,
                             secret,
                             algorithms=["HS256"],
                             # issuer="expense-tracker-auth", enforce strict checking
                             options={"require": ["exp", "iss", "sub", "userId"]},
                             )
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

    user_id = payload.get("userId")
    if not user_id:
        raise HTTPException(status_code=401, detail="Token missing userId claim")
    return user_id
