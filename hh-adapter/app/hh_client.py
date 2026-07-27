from __future__ import annotations

import os
from typing import Any
from requests.utils import requote_uri

try:
    from curl_cffi import requests as _cffi_requests
    from curl_cffi.requests import Session as _CffiSession
    HAS_CFFI = True
except Exception:
    _cffi_requests = None
    _CffiSession = None
    HAS_CFFI = False

import requests as _requests


HH_BASE = "https://hh.ru"
_IMPERSONATE = os.environ.get("HH_IMPERSONATE", "chrome124")
_SKIP_HEADERS = {"cookie", "content-length", "host"}


def _safe_header_value(name: str, value: Any) -> str | None:
    if value is None:
        return None
    text = str(value)
    if name.lower() in {"referer", "origin"}:
        text = requote_uri(text)
    try:
        text.encode("latin-1")
    except UnicodeEncodeError:
        return None
    return text


def sanitize_headers(headers: dict[str, Any] | None) -> dict[str, str]:
    safe = {}
    for key, value in (headers or {}).items():
        name = str(key)
        if name.lower() in _SKIP_HEADERS:
            continue
        safe_value = _safe_header_value(name, value)
        if safe_value is not None:
            safe[name] = safe_value
    return safe


class HHClient:
    def __init__(self):
        self._cffi = _CffiSession() if HAS_CFFI else None
        self._requests = _requests.Session()

    def request(self, method: str, url: str, **kwargs) -> Any:
        if "headers" in kwargs:
            kwargs["headers"] = sanitize_headers(kwargs.get("headers"))
        if self._cffi is not None:
            try:
                return self._cffi.request(method, url, impersonate=_IMPERSONATE, **kwargs)
            except Exception:
                pass
        return self._requests.request(method, url, **kwargs)

    def get(self, url: str, **kwargs) -> Any:
        return self.request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> Any:
        return self.request("POST", url, **kwargs)

    def close(self) -> None:
        for session in (self._cffi, self._requests):
            if session is not None:
                try:
                    session.close()
                except Exception:
                    pass

class IsolatedHHClient:
    def request(self, method: str, url: str, **kwargs) -> Any:
        client = HHClient()
        try:
            return client.request(method, url, **kwargs)
        finally:
            client.close()

    def get(self, url: str, **kwargs) -> Any:
        return self.request("GET", url, **kwargs)

    def post(self, url: str, **kwargs) -> Any:
        return self.request("POST", url, **kwargs)


HH = IsolatedHHClient()


def is_login_page(html: str) -> bool:
    text = (html or "")[:20000].lower()
    return (
        "account/login" in text
        or "data-qa=\"account-login\"" in text
        or "need-login" in text
        or "войдите" in text and "пароль" in text
    )
