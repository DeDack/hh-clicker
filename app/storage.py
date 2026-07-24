from __future__ import annotations

import json
import os
import tempfile
import threading
from pathlib import Path

from app.models import SessionData


DATA_DIR = Path(os.environ.get("HH_CLEAN_DATA_DIR", "../hh-clicker-clean-userdata"))
SETTINGS_FILE = DATA_DIR / "settings.json"
APPLICATIONS_FILE = DATA_DIR / "applications.json"
_LOCK = threading.RLock()


def _read_json(path: Path, default):
    try:
        if not path.exists():
            return default
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:
        return default


def _write_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=str(path.parent))
    os.close(fd)
    Path(tmp).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(tmp, path)
    try:
        os.chmod(path, 0o600)
    except Exception:
        pass


def load_session() -> SessionData:
    with _LOCK:
        data = _read_json(SETTINGS_FILE, {})
    return SessionData(
        cookies=data.get("cookies") if isinstance(data.get("cookies"), dict) else {},
        headers=data.get("headers") if isinstance(data.get("headers"), dict) else {},
        resumes=data.get("resumes") if isinstance(data.get("resumes"), list) else [],
        selected_resume_hash=str(data.get("selected_resume_hash") or ""),
        cover_letter=str(data.get("cover_letter") or ""),
        last_search_url=str(data.get("last_search_url") or ""),
        last_recommendation_url=str(data.get("last_recommendation_url") or ""),
        recommendation_keyword=str(data.get("recommendation_keyword") or ""),
        recommendation_exclude_keywords=str(data.get("recommendation_exclude_keywords") or ""),
        pages=int(data.get("pages") or 1),
        delay_seconds=float(data.get("delay_seconds") or 1.0),
    )


def save_session(session: SessionData) -> None:
    with _LOCK:
        _write_json(SETTINGS_FILE, {
            "cookies": session.cookies,
            "headers": {
                k: v for k, v in session.headers.items()
                if k.lower() in {"user-agent", "x-xsrftoken", "x-xsrf-token", "accept-language"}
            },
            "resumes": session.resumes,
            "selected_resume_hash": session.selected_resume_hash,
            "cover_letter": session.cover_letter,
            "last_search_url": session.last_search_url,
            "last_recommendation_url": session.last_recommendation_url,
            "recommendation_keyword": session.recommendation_keyword,
            "recommendation_exclude_keywords": session.recommendation_exclude_keywords,
            "pages": session.pages,
            "delay_seconds": session.delay_seconds,
        })


def load_applications() -> dict:
    with _LOCK:
        data = _read_json(APPLICATIONS_FILE, {"applied": {}})
    if not isinstance(data, dict):
        data = {"applied": {}}
    data.setdefault("applied", {})
    return data


def save_applications(data: dict) -> None:
    with _LOCK:
        _write_json(APPLICATIONS_FILE, data)


def is_applied(resume_hash: str, vacancy_id: str) -> bool:
    data = load_applications()
    return vacancy_id in (data.get("applied", {}).get(resume_hash, {}) or {})


def mark_applied(resume_hash: str, vacancy_id: str, info: dict) -> None:
    data = load_applications()
    data.setdefault("applied", {}).setdefault(resume_hash, {})[vacancy_id] = info
    save_applications(data)


def clear_user_data() -> None:
    with _LOCK:
        for path in (SETTINGS_FILE, APPLICATIONS_FILE):
            try:
                path.unlink()
            except FileNotFoundError:
                pass
