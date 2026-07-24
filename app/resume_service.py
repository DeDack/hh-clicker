from __future__ import annotations

import json
import re
import urllib.parse

from bs4 import BeautifulSoup

from app.hh_client import HH, HH_BASE, is_login_page
from app.models import SessionData


RESUME_HASH_RE = re.compile(r"^[0-9a-f]{20,80}$", re.IGNORECASE)
RESUME_LINK_RE = re.compile(r"^/resume/([0-9a-f]{20,80})(?:/)?$", re.IGNORECASE)


def parse_hh_lux_ssr(html: str) -> dict:
    match = re.search(r'<template[^>]*id="HH-Lux-InitialState"[^>]*>([\s\S]*?)</template>', html or "")
    if not match:
        return {}
    try:
        return json.loads(match.group(1))
    except Exception:
        return {}


def _resume_hash_from_href(href: str) -> str:
    try:
        parsed = urllib.parse.urlparse(href or "")
    except Exception:
        return ""
    if parsed.scheme and parsed.scheme not in {"http", "https"}:
        return ""
    if parsed.netloc:
        host = (parsed.hostname or "").lower()
        if host != "hh.ru":
            return ""
    match = RESUME_LINK_RE.match(parsed.path or "")
    return match.group(1) if match else ""


def _clean_title(value: str) -> str:
    value = re.sub(r"\s+", " ", value or "").strip()
    for marker in ("Редактировать", "Обновить", "Поднять", "Скачать", "Удалить"):
        idx = value.find(marker)
        if idx > 2:
            value = value[:idx].strip()
    return value[:160] or "Резюме"


def parse_resumes_from_html(html: str) -> list[dict]:
    soup = BeautifulSoup(html or "", "html.parser")
    out = []
    seen = set()
    for link in soup.find_all("a", href=True):
        resume_hash = _resume_hash_from_href(link.get("href", ""))
        if not resume_hash or resume_hash in seen:
            continue
        seen.add(resume_hash)
        title = _clean_title(link.get_text(" ", strip=True) or link.get("title", ""))
        out.append({"hash": resume_hash, "title": title})
    return out


def _base_headers(session: SessionData) -> dict:
    headers = {
        "User-Agent": session.headers.get("User-Agent") or "Mozilla/5.0",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": session.headers.get("Accept-Language", "ru,en;q=0.9"),
        "Upgrade-Insecure-Requests": "1",
    }
    if session.cookies.get("_xsrf"):
        headers["X-XSRFToken"] = session.cookies["_xsrf"]
    return headers


def fetch_resumes(session: SessionData) -> tuple[bool, str, list[dict]]:
    if not session.cookies.get("hhtoken"):
        return False, "Не найден hhtoken в cookies", []
    try:
        try:
            HH.get(HH_BASE + "/", headers=_base_headers(session), cookies=session.cookies, timeout=10)
        except Exception:
            pass
        response = HH.get(
            HH_BASE + "/applicant/resumes",
            headers={**_base_headers(session), "Referer": HH_BASE + "/"},
            cookies=session.cookies,
            timeout=15,
            allow_redirects=True,
        )
    except Exception as exc:
        return False, f"Ошибка сети при проверке сессии: {exc}", []

    if response.status_code != 200 or is_login_page(response.text):
        return False, f"Сессия HH не подтверждена: HTTP {response.status_code}", []

    ssr = parse_hh_lux_ssr(response.text)
    resumes = []
    for item in ssr.get("applicantResumes", []) if isinstance(ssr.get("applicantResumes"), list) else []:
        attrs = item.get("_attributes", {}) if isinstance(item, dict) else {}
        nested = item.get("resume", {}) if isinstance(item, dict) else {}
        resume_hash = attrs.get("hash") or nested.get("hash") or ""
        title = attrs.get("title") or item.get("title", "") or nested.get("title", "")
        if resume_hash:
            resumes.append({"hash": resume_hash, "title": title or "Резюме"})
    if not resumes:
        resumes = parse_resumes_from_html(response.text)
    latest = ssr.get("latestResumeHash", "") if isinstance(ssr, dict) else ""
    if latest and not any(r["hash"] == latest for r in resumes):
        resumes.insert(0, {"hash": latest, "title": "Резюме"})
    return True, "Сессия подтверждена", resumes
