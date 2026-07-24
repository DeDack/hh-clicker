from __future__ import annotations

import os
from typing import Any

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


class HHClient:
    def __init__(self):
        self._cffi = _CffiSession() if HAS_CFFI else None
        self._requests = _requests.Session()

    def request(self, method: str, url: str, **kwargs) -> Any:
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


HH = HHClient()


def is_login_page(html: str) -> bool:
    text = (html or "")[:20000].lower()
    return (
        "account/login" in text
        or "data-qa=\"account-login\"" in text
        or "need-login" in text
        or "войдите" in text and "пароль" in text
    )
