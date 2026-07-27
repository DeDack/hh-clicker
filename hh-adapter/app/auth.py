from __future__ import annotations

import hmac
import os

from fastapi import Header, HTTPException


def _configured_key() -> str:
    return os.environ.get("HH_ADAPTER_API_KEY") or os.environ.get("INTERNAL_API_KEY", "")


async def require_internal_api_key(x_internal_api_key: str = Header(default="")) -> None:
    expected = _configured_key()
    if not expected or not hmac.compare_digest(x_internal_api_key, expected):
        raise HTTPException(status_code=401, detail={"code": "UNAUTHORIZED", "message": "Invalid internal API key"})
