import base64
import time

import jwt
import pytest
from fastapi import HTTPException

from app.auth import get_current_user_id

SECRET_RAW = b"super-secret-key-that-is-at-least-32-bytes-long!!"
SECRET_B64 = base64.b64encode(SECRET_RAW).decode("utf-8")

def _generate_token(
    user_id="user-123",
    sub="testuser",
    issuer="expense-tracker-auth",
    exp_delta=3600,
    secret=SECRET_RAW,
    omit_fields=None,
):
    omit_fields = omit_fields or []
    payload = {
        "sub": sub,
        "userId": user_id,
        "iss": issuer,
        "iat": int(time.time()),
        "exp": int(time.time()) + exp_delta,
    }
    for field in omit_fields:
        payload.pop(field, None)
    return jwt.encode(payload, secret, algorithm="HS256")


@pytest.fixture(autouse=True)
def set_jwt_env(monkeypatch):
    monkeypatch.setenv("JWT_SECRET", SECRET_B64)


def test_valid_token_returns_userId():
    token=_generate_token(user_id="user-42" )
    userId= get_current_user_id(authorization=f"Bearer {token}")
    assert userId == "user-42"

def test_missing_header_raises_401():
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization=None)
    assert e.value.status_code == 401
    assert "Missing Authorization header" in e.value.detail


def test_malformed_header_missing_bearer_prefix_raises_401():
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization="Basic ddsdasdsafsafs")
    assert e.value.status_code == 401
    assert "Malformed Authorization header" in e.value.detail

def test_expired_token_raises_401():
    token= _generate_token(exp_delta= -3600)
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization=f"Bearer {token}")
    assert e.value.status_code == 401
    assert "Token expired" in e.value.detail


def test_tampered_signature_raises_401():
    token = _generate_token(secret=b"different-secret-key-32-bytes-long!")
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization=f"Bearer {token}")
    assert e.value.status_code == 401
    assert "Invalid token" in e.value.detail


# def test_invalid_issuer_raises_401():
#     token = _generate_token(issuer="rogue-issuer")
#     with pytest.raises(HTTPException) as e:
#         get_current_user_id(authorization=f"Bearer {token}")
#     assert e.value.status_code == 401
#     assert "Invalid token" in e.value.detail


def test_missing_user_id_claim_raises_401():
    token = _generate_token(omit_fields=["userId"])
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization=f"Bearer {token}")
    assert e.value.status_code == 401
    assert "Invalid token" in e.value.detail


def test_missing_jwt_secret_env_raises_500(monkeypatch):
    monkeypatch.delenv("JWT_SECRET", raising=False)
    token = _generate_token()
    with pytest.raises(HTTPException) as e:
        get_current_user_id(authorization=f"Bearer {token}")
    assert e.value.status_code == 500
    assert "JWT_SECRET is not configured" in e.value.detail




