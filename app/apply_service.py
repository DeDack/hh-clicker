from __future__ import annotations

import json

from app.hh_client import HH, HH_BASE, is_login_page
from app.models import SessionData


def classify_apply_response(status_code: int, text: str) -> tuple[str, dict]:
    if status_code in (401, 403):
        return "auth_error", {}
    if status_code == 200 and is_login_page(text):
        return "auth_error", {}

    info = {}
    parsed = None
    if (text or "").lstrip().startswith("{"):
        try:
            parsed = json.loads(text)
        except Exception:
            parsed = None
    if isinstance(parsed, dict):
        error = str(parsed.get("error") or "").lower()
        if "test-required" in error or "test_required" in error:
            return "test", info
        if "limit" in error or "negotiations-limit" in error:
            return "limit", info
        if "already" in error:
            return "already", info
        status = parsed.get("responseStatus") or {}
        if status.get("alreadyApplied") is True:
            return "already", info
        if status.get("test-required") is True or status.get("testRequired") is True:
            return "test", info
        if status.get("negotiationsLimitExceeded") is True or status.get("negotiations-limit-exceeded") is True:
            return "limit", info
        if parsed.get("test-required") is True or parsed.get("testRequired") is True:
            return "test", info
        if parsed.get("alreadyApplied") is True:
            return "already", info
        if parsed.get("negotiationsLimitExceeded") is True or parsed.get("negotiations-limit-exceeded") is True:
            return "limit", info
        if parsed.get("success") in (True, "true", "True") or parsed.get("topic_id") or status.get("responded") is True:
            return "sent", {"topic_id": parsed.get("topic_id", ""), "chat_id": parsed.get("chat_id", "")}

    if "negotiations-limit-exceeded" in text:
        return "limit", info
    if "test-required" in text:
        return "test", info
    if "alreadyApplied" in text:
        return "already", info
    if status_code == 200 and (
        '"success":true' in text or '"status":"ok"' in text or '"responded":true' in text or "topic_id" in text
    ):
        return "sent", info
    return "error", {"http_status": status_code}


def send_apply(session: SessionData, vacancy_id: str, cover_letter: str) -> tuple[str, dict]:
    xsrf = session.cookies.get("_xsrf", "")
    if not xsrf:
        return "error", {"exception": "Missing _xsrf token"}
    headers = {
        "User-Agent": session.headers.get("User-Agent") or "Mozilla/5.0",
        "Origin": HH_BASE,
        "Referer": f"{HH_BASE}/vacancy/{vacancy_id}",
        "X-Xsrftoken": xsrf,
    }
    data = {
        "resume_hash": session.selected_resume_hash,
        "vacancy_id": vacancy_id,
        "letterRequired": "true",
        "letter": cover_letter,
        "lux": "true",
        "ignore_postponed": "true",
    }
    try:
        response = HH.post(
            HH_BASE + "/applicant/vacancy_response/popup",
            headers=headers,
            cookies=session.cookies,
            data=data,
            timeout=20,
        )
        return classify_apply_response(response.status_code, response.text or "")
    except Exception as exc:
        return "error", {"exception": str(exc)}
