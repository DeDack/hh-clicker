from __future__ import annotations

import re
import shlex
from dataclasses import dataclass


@dataclass(frozen=True)
class ParsedCurl:
    url: str
    cookies: dict
    headers: dict

    def public(self) -> dict:
        return {
            "url": self.url,
            "cookies_count": len(self.cookies),
            "has_hhtoken": bool(self.cookies.get("hhtoken")),
            "has_xsrf": bool(self.cookies.get("_xsrf") or self.headers.get("X-XSRFToken")),
            "user_agent": self.headers.get("User-Agent", ""),
        }


def _decode_cookie_line(raw_line: str) -> str:
    if "\\" not in raw_line:
        return raw_line
    try:
        return raw_line.encode("utf-8").decode("unicode_escape")
    except (UnicodeDecodeError, UnicodeEncodeError):
        return raw_line


def _parse_cookie_line(raw_line: str) -> dict:
    cookies = {}
    for part in raw_line.split(";"):
        part = part.strip()
        if "=" not in part:
            continue
        key, value = part.split("=", 1)
        key = key.strip()
        value = "".join(
            ch for ch in value.strip()
            if ch == "\t" or 0x20 <= ord(ch) < 0x7f or ord(ch) >= 0xa0
        )
        if key and value:
            cookies[key] = value
    return cookies


def parse_curl(raw: str) -> ParsedCurl:
    raw = (raw or "").strip()
    if not raw:
        raise ValueError("empty cURL")
    if "\\u00" in raw:
        raw = raw.encode().decode("unicode_escape", errors="replace")

    url = ""
    headers = {}
    cookie_line = ""

    if raw.startswith("curl "):
        try:
            parts = shlex.split(raw)
        except ValueError:
            parts = re.findall(r"""['"]([^'"]+)['"]|(\S+)""", raw)
            parts = [a or b for a, b in parts]

        i = 1
        while i < len(parts):
            part = parts[i]
            nxt = parts[i + 1] if i + 1 < len(parts) else ""
            if part in ("-H", "--header") and nxt:
                if ":" in nxt:
                    key, value = nxt.split(":", 1)
                    key = key.strip()
                    value = value.strip()
                    if key.lower() == "cookie":
                        cookie_line = value
                    elif key.lower() in {"user-agent", "x-xsrftoken", "x-xsrf-token", "accept-language"}:
                        canonical = "User-Agent" if key.lower() == "user-agent" else key
                        headers[canonical] = value
                i += 2
                continue
            if part in ("-b", "--cookie") and nxt:
                cookie_line = nxt
                i += 2
                continue
            if part.startswith(("http://", "https://")) and not url:
                url = part
            i += 1
    elif raw.lower().startswith("cookie:"):
        cookie_line = raw[7:].strip()
    else:
        cookie_line = raw

    cookie_line = _decode_cookie_line(cookie_line)
    cookies = _parse_cookie_line(cookie_line)
    if "User-Agent" not in headers:
        headers["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    if cookies.get("_xsrf"):
        headers.setdefault("X-XSRFToken", cookies["_xsrf"])
    return ParsedCurl(url=url, cookies=cookies, headers=headers)
