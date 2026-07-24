from __future__ import annotations

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates

from app.curl_parser import parse_curl
from app.models import RunConfig
from app.resume_service import fetch_resumes
from app.search_service import SNAPSHOTS, preview_search
from app.storage import clear_user_data, load_session, save_session
from app.worker import WORKER


router = APIRouter()
templates = Jinja2Templates(directory="app/templates")


@router.get("/", response_class=HTMLResponse)
async def index(request: Request):
    return templates.TemplateResponse(request, "index.html")


@router.get("/health")
async def health():
    return {"ok": True}


@router.post("/api/session/import")
async def import_session(payload: dict):
    raw = payload.get("curl", "")
    try:
        parsed = parse_curl(raw)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    if not parsed.cookies.get("hhtoken"):
        raise HTTPException(status_code=400, detail="Не найден hhtoken в cURL/cookies")
    session = load_session()
    session.cookies = parsed.cookies
    session.headers = parsed.headers
    ok, message, resumes = fetch_resumes(session)
    if not ok:
        save_session(session)
        raise HTTPException(status_code=401, detail=message)
    session.resumes = resumes
    if resumes and not session.selected_resume_hash:
        session.selected_resume_hash = resumes[0]["hash"]
    save_session(session)
    return {"ok": True, "message": message, "session": session.public(), "curl": None}


@router.get("/api/session/check")
async def check_session():
    session = load_session()
    if not session.cookies:
        return {"ok": False, "alive": False, "message": "Сессия не импортирована", "session": session.public()}
    ok, message, resumes = fetch_resumes(session)
    if ok:
        session.resumes = resumes
        if resumes and session.selected_resume_hash not in {r["hash"] for r in resumes}:
            session.selected_resume_hash = resumes[0]["hash"]
        save_session(session)
    return {"ok": ok, "alive": ok, "message": message, "session": session.public()}


@router.post("/api/session/clear")
async def clear_session():
    clear_user_data()
    return {"ok": True, "message": "Локальные данные очищены", "session": load_session().public()}


@router.get("/api/resumes")
async def get_resumes():
    session = load_session()
    ok, message, resumes = fetch_resumes(session)
    if ok:
        session.resumes = resumes
        if resumes and session.selected_resume_hash not in {r["hash"] for r in resumes}:
            session.selected_resume_hash = resumes[0]["hash"]
        save_session(session)
    return {"ok": ok, "message": message, "session": session.public()}


@router.post("/api/resume/select")
async def select_resume(payload: dict):
    session = load_session()
    resume_hash = str(payload.get("resume_hash") or "")
    if resume_hash not in {r.get("hash") for r in session.resumes}:
        raise HTTPException(status_code=400, detail="Unknown resume_hash")
    session.selected_resume_hash = resume_hash
    save_session(session)
    return {"ok": True, "session": session.public()}


@router.post("/api/settings")
async def save_settings(payload: dict):
    session = load_session()
    session.cover_letter = str(payload.get("cover_letter") or "")
    if payload.get("search_url") is not None:
        session.last_search_url = str(payload.get("search_url") or "")
    if payload.get("recommendation_url") is not None:
        session.last_recommendation_url = str(payload.get("recommendation_url") or "")
    if payload.get("recommendation_keyword") is not None:
        session.recommendation_keyword = str(payload.get("recommendation_keyword") or "")
    if payload.get("pages") is not None:
        session.pages = max(1, min(int(payload.get("pages") or 1), 50))
    if payload.get("delay_seconds") is not None:
        session.delay_seconds = max(0.0, float(payload.get("delay_seconds") or 0))
    save_session(session)
    return {"ok": True, "session": session.public()}


@router.post("/api/search/preview")
async def search_preview(payload: dict):
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме")
    search_url = str(payload.get("search_url") or "").strip()
    pages = int(payload.get("pages") or 1)
    result = preview_search(session, search_url, pages)
    session.last_search_url = search_url
    session.pages = pages
    save_session(session)
    return {"ok": True, **result}


@router.post("/api/search/recommendations/preview")
async def recommendations_preview(payload: dict):
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме")
    search_url = str(payload.get("recommendation_url") or payload.get("search_url") or "").strip()
    keyword = str(payload.get("recommendation_keyword") or "").strip()
    pages = int(payload.get("pages") or 1)
    result = preview_search(session, search_url, pages, title_keyword=keyword)
    session.last_recommendation_url = search_url
    session.recommendation_keyword = keyword
    session.pages = pages
    save_session(session)
    return {"ok": True, **result}


@router.post("/api/search/snapshot/remove")
async def snapshot_remove(payload: dict):
    snapshot_id = str(payload.get("snapshot_id") or "")
    vacancy_id = str(payload.get("vacancy_id") or "")
    try:
        snapshot = SNAPSHOTS.remove_vacancy(snapshot_id, vacancy_id)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    return {"ok": True, **snapshot}


@router.post("/api/run/start")
async def run_start(payload: dict):
    session = load_session()
    snapshot_id = str(payload.get("snapshot_id") or "")
    snap = SNAPSHOTS.get(snapshot_id)
    if not snap:
        raise HTTPException(status_code=400, detail="Unknown snapshot_id")
    dry_run = not bool(payload.get("allow_real_apply", False))
    config = RunConfig(
        resume_hash=session.selected_resume_hash,
        cover_letter=str(payload.get("cover_letter") if payload.get("cover_letter") is not None else session.cover_letter),
        search_url=snap["search_url"],
        pages=max((v.source_page for v in snap["vacancies"]), default=0) + 1,
        delay_seconds=max(0.0, float(payload.get("delay_seconds") if payload.get("delay_seconds") is not None else session.delay_seconds)),
        dry_run=dry_run,
    )
    WORKER.start(snapshot_id, config, session)
    return {"ok": True, "state": WORKER.public_state(), "dry_run": dry_run}


@router.post("/api/run/stop")
async def run_stop():
    WORKER.stop()
    return {"ok": True, "state": WORKER.public_state()}


@router.get("/api/run/state")
async def run_state():
    session = load_session()
    return {"ok": True, "session": session.public(), "worker": WORKER.public_state()}
