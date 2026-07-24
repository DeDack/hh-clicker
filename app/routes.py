from __future__ import annotations

import re
import logging
import asyncio
import time
from urllib.parse import urlsplit

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse
from fastapi.templating import Jinja2Templates

from app.apply_service import send_apply
from app.curl_parser import parse_curl
from app.cover_letter_service import (
    CoverLetterGenerationService,
    get_cached_resume_data,
    fetch_vacancy_data,
    settings_from_session,
    sha256_text,
)
from app.llm_client import KomapiAnthropicClient, LlmError, create_llm_client, load_llm_settings
from app.models import RunConfig, VacancyData
from app.resume_service import fetch_resumes
from app.search_service import SNAPSHOTS, preview_search
from app.storage import clear_user_data, load_session, save_session
from app.worker import WORKER


router = APIRouter()
templates = Jinja2Templates(directory="app/templates")
COVER_LETTERS = CoverLetterGenerationService()
LOGGER = logging.getLogger("uvicorn.error")
SINGLE_HH_FETCH_TIMEOUT_SECONDS = 25


def _bool_payload(payload: dict, key: str, default: bool) -> bool:
    if payload.get(key) is None:
        return default
    return bool(payload.get(key))


def _vacancy_id_from_url(url: str) -> str:
    split = urlsplit((url or "").strip())
    if split.scheme != "https":
        raise ValueError("vacancy URL must use https")
    hostname = split.hostname or ""
    if hostname != "hh.ru" and not hostname.endswith(".hh.ru"):
        raise ValueError("vacancy URL host must be hh.ru or an hh.ru subdomain")
    match = re.search(r"/vacancy/(\d+)", split.path)
    if not match:
        raise ValueError("vacancy URL must contain /vacancy/{id}")
    return match.group(1)


async def _generate_snapshot_cover_letters(session, snapshot_id: str, vacancy_id: str = "", force: bool = False) -> dict:
    snap = SNAPSHOTS.get(snapshot_id)
    if not snap:
        raise ValueError("unknown snapshot_id")
    targets = [v for v in snap["vacancies"] if not vacancy_id or v.id == vacancy_id]
    if vacancy_id and not targets:
        raise ValueError("unknown vacancy_id")
    try:
        resume = get_cached_resume_data(session, snap["resume_hash"])
    except Exception as exc:
        error = f"resume: {type(exc).__name__}: {str(exc)[:180]}"
        for vacancy in targets:
            SNAPSHOTS.set_cover_letter_status(snapshot_id, vacancy.id, "FAILED", error)
        return SNAPSHOTS.public(snapshot_id)
    settings = settings_from_session(session)
    previous_letters = [
        e.get("coverLetter", "")
        for e in SNAPSHOTS.cover_letter_entries(snapshot_id)
        if e.get("coverLetterStatus") in {"GENERATED", "EDITED"} and e.get("vacancyId") != vacancy_id
    ]
    for vacancy in targets:
        existing = SNAPSHOTS.get_cover_letter(snapshot_id, vacancy.id) or {}
        if existing.get("coverLetterEditedManually") and not force:
            continue
        SNAPSHOTS.set_cover_letter_status(snapshot_id, vacancy.id, "GENERATING", None)
        try:
            vacancy_data = fetch_vacancy_data(session, vacancy.id, vacancy.title)
            vacancy_hash = sha256_text(vacancy_data.description)
            if (
                not force
                and existing.get("coverLetterStatus") == "GENERATED"
                and existing.get("resumeHash") == resume.hash
                and existing.get("vacancyDescriptionHash") == vacancy_hash
                and existing.get("coverLetter")
            ):
                previous_letters.append(existing["coverLetter"])
                continue
            result = await COVER_LETTERS.generate(resume, vacancy_data, settings, previous_letters, force)
            saved = SNAPSHOTS.save_cover_letter_result(
                snapshot_id,
                vacancy.id,
                result,
                vacancy_data.title,
                vacancy_data.company_name,
                vacancy_hash,
                resume.hash,
            )
            if saved.get("coverLetter"):
                previous_letters.append(saved["coverLetter"])
        except Exception as exc:
            SNAPSHOTS.set_cover_letter_status(snapshot_id, vacancy.id, "FAILED", f"{type(exc).__name__}: {str(exc)[:180]}")
    return SNAPSHOTS.public(snapshot_id)


@router.get("/", response_class=HTMLResponse)
async def index(request: Request):
    return templates.TemplateResponse(request, "index.html")


@router.get("/single", response_class=HTMLResponse)
async def single_apply_page(request: Request):
    return templates.TemplateResponse(request, "single.html")


@router.get("/health")
async def health():
    return {"ok": True}


@router.get("/api/llm/status")
async def llm_status():
    settings = load_llm_settings()
    if settings.provider != "komapi":
        client = create_llm_client(settings)
        try:
            return {
                "configured": bool(settings.openai_api_key),
                "provider": settings.provider,
                "reachable": bool(settings.openai_api_key),
                "model": client.model,
                "modelAvailable": None,
                "availableModels": [],
            }
        finally:
            close = getattr(client, "aclose", None)
            if close:
                await close()
    client = KomapiAnthropicClient(settings)
    try:
        return await client.check_status()
    finally:
        await client.aclose()


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


@router.post("/api/resume/cache")
async def cache_selected_resume():
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме")
    started = time.monotonic()
    try:
        LOGGER.info("resume_cache refresh start resume=%s", session.selected_resume_hash[:10])
        resume = await asyncio.wait_for(
            asyncio.to_thread(get_cached_resume_data, session, session.selected_resume_hash, True),
            timeout=SINGLE_HH_FETCH_TIMEOUT_SECONDS,
        )
        LOGGER.info("resume_cache refresh done resume_hash=%s elapsed=%.2fs", resume.hash[:10], time.monotonic() - started)
    except TimeoutError:
        LOGGER.warning("resume_cache refresh timeout resume=%s timeout=%ss", session.selected_resume_hash[:10], SINGLE_HH_FETCH_TIMEOUT_SECONDS)
        raise HTTPException(status_code=504, detail="Timeout while loading resume from HH")
    except Exception as exc:
        LOGGER.warning("resume_cache refresh error resume=%s error=%s", session.selected_resume_hash[:10], type(exc).__name__)
        raise HTTPException(status_code=502, detail=f"Не удалось сохранить текст резюме: {type(exc).__name__}")
    return {"ok": True, "resume_hash": session.selected_resume_hash, "text_hash": resume.hash, "title": resume.title}


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
    if payload.get("recommendation_exclude_keywords") is not None:
        session.recommendation_exclude_keywords = str(payload.get("recommendation_exclude_keywords") or "")
    if payload.get("pages") is not None:
        session.pages = max(1, min(int(payload.get("pages") or 1), 50))
    if payload.get("delay_seconds") is not None:
        session.delay_seconds = max(0.0, float(payload.get("delay_seconds") or 0))
    if payload.get("max_applications") is not None:
        session.max_applications = max(0, int(payload.get("max_applications") or 0))
    if payload.get("cover_letter_mode") in {"common", "personal"}:
        session.cover_letter_mode = str(payload.get("cover_letter_mode"))
    if payload.get("cover_letter_style") is not None:
        session.cover_letter_style = str(payload.get("cover_letter_style") or "живой")
    if payload.get("cover_letter_length") is not None:
        session.cover_letter_length = str(payload.get("cover_letter_length") or "среднее")
    session.cover_letter_use_company = _bool_payload(payload, "cover_letter_use_company", session.cover_letter_use_company)
    session.cover_letter_use_vacancy_title = _bool_payload(payload, "cover_letter_use_vacancy_title", session.cover_letter_use_vacancy_title)
    session.cover_letter_auto_generate = _bool_payload(payload, "cover_letter_auto_generate", session.cover_letter_auto_generate)
    session.cover_letter_allow_empty_fallback = _bool_payload(payload, "cover_letter_allow_empty_fallback", session.cover_letter_allow_empty_fallback)
    if payload.get("cover_letter_max_attempts") is not None:
        session.cover_letter_max_attempts = max(1, min(int(payload.get("cover_letter_max_attempts") or 2), 5))
    save_session(session)
    return {"ok": True, "session": session.public()}


@router.post("/api/search/preview")
async def search_preview(payload: dict):
    return await _search_preview_common(payload)


async def _search_preview_common(payload: dict) -> dict:
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме")
    search_url = str(payload.get("search_url") or payload.get("recommendation_url") or "").strip()
    keyword = str(payload.get("title_keyword") or payload.get("recommendation_keyword") or "").strip()
    exclude_keywords = str(payload.get("exclude_title_keywords") or payload.get("recommendation_exclude_keywords") or "").strip()
    pages = int(payload.get("pages") or 1)
    result = preview_search(session, search_url, pages, title_keyword=keyword, exclude_title_keywords=exclude_keywords)
    if session.cover_letter_mode == "personal" and session.cover_letter_auto_generate:
        result = await _generate_snapshot_cover_letters(session, result["snapshot_id"])
    session.last_search_url = search_url
    session.last_recommendation_url = search_url
    session.recommendation_keyword = keyword
    session.recommendation_exclude_keywords = exclude_keywords
    session.pages = pages
    save_session(session)
    return {"ok": True, **result}


@router.post("/api/search/recommendations/preview")
async def recommendations_preview(payload: dict):
    return await _search_preview_common(payload)


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


@router.post("/api/preview/{snapshot_id}/cover-letters/generate")
async def cover_letters_generate(snapshot_id: str):
    session = load_session()
    try:
        result = await _generate_snapshot_cover_letters(session, snapshot_id, force=False)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return {"ok": True, **result}


@router.post("/api/preview/{snapshot_id}/vacancies/{vacancy_id}/cover-letter/regenerate")
async def cover_letter_regenerate(snapshot_id: str, vacancy_id: str):
    session = load_session()
    try:
        result = await _generate_snapshot_cover_letters(session, snapshot_id, vacancy_id, force=True)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    return {"ok": True, **result}


@router.put("/api/preview/{snapshot_id}/vacancies/{vacancy_id}/cover-letter")
async def cover_letter_update(snapshot_id: str, vacancy_id: str, payload: dict):
    try:
        entry = SNAPSHOTS.update_cover_letter(snapshot_id, vacancy_id, str(payload.get("cover_letter") or ""))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except RuntimeError as exc:
        raise HTTPException(status_code=409, detail=str(exc))
    return {"ok": True, "coverLetter": entry, **SNAPSHOTS.public(snapshot_id)}


@router.get("/api/preview/{snapshot_id}/cover-letters/status")
async def cover_letters_status(snapshot_id: str):
    snap = SNAPSHOTS.public(snapshot_id)
    if not snap:
        raise HTTPException(status_code=400, detail="Unknown snapshot_id")
    return {"ok": True, **snap}


@router.post("/api/single/vacancy")
async def single_vacancy(payload: dict):
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL на главной странице")
    try:
        vacancy_id = _vacancy_id_from_url(str(payload.get("vacancy_url") or ""))
        started = time.monotonic()
        LOGGER.info("single_vacancy fetch_vacancy start vacancy=%s", vacancy_id)
        vacancy = await asyncio.wait_for(
            asyncio.to_thread(fetch_vacancy_data, session, vacancy_id),
            timeout=SINGLE_HH_FETCH_TIMEOUT_SECONDS,
        )
        LOGGER.info("single_vacancy fetch_vacancy done vacancy=%s elapsed=%.2fs", vacancy_id, time.monotonic() - started)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except TimeoutError:
        LOGGER.warning("single_vacancy fetch_vacancy timeout vacancy=%s timeout=%ss", vacancy_id if "vacancy_id" in locals() else "", SINGLE_HH_FETCH_TIMEOUT_SECONDS)
        raise HTTPException(status_code=504, detail="Timeout while loading vacancy from HH")
    except Exception as exc:
        LOGGER.warning("single_vacancy fetch_vacancy error vacancy=%s error=%s", vacancy_id if "vacancy_id" in locals() else "", type(exc).__name__)
        raise HTTPException(status_code=502, detail=f"Не удалось загрузить вакансию: {type(exc).__name__}: {str(exc)[:180]}")
    return {
        "ok": True,
        "vacancy": {
            "id": vacancy.id,
            "title": vacancy.title,
            "url": vacancy.url,
            "company_name": vacancy.company_name,
            "description": vacancy.description,
            "description_hash": sha256_text(vacancy.description),
            "questions": vacancy.questions,
        },
    }


@router.post("/api/single/cover-letter/generate")
async def single_cover_letter_generate(payload: dict):
    request_started = time.monotonic()
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL на главной странице")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме на главной странице")
    vacancy_url = str(payload.get("vacancy_url") or "")
    LOGGER.info("single_cover_letter start resume=%s vacancy_url_present=%s", session.selected_resume_hash[:10], bool(vacancy_url))
    try:
        vacancy_id = _vacancy_id_from_url(vacancy_url)
        step_started = time.monotonic()
        LOGGER.info("single_cover_letter load_resume start resume=%s vacancy=%s", session.selected_resume_hash[:10], vacancy_id)
        resume = await asyncio.wait_for(
            asyncio.to_thread(get_cached_resume_data, session, session.selected_resume_hash),
            timeout=SINGLE_HH_FETCH_TIMEOUT_SECONDS,
        )
        LOGGER.info("single_cover_letter load_resume done resume_hash=%s vacancy=%s elapsed=%.2fs", resume.hash[:10], vacancy_id, time.monotonic() - step_started)
        vacancy_payload = payload.get("vacancy") if isinstance(payload.get("vacancy"), dict) else None
        if vacancy_payload and str(vacancy_payload.get("id") or "") == vacancy_id and str(vacancy_payload.get("description") or "").strip():
            step_started = time.monotonic()
            vacancy = VacancyData(
                id=vacancy_id,
                title=str(vacancy_payload.get("title") or ""),
                url=str(vacancy_payload.get("url") or vacancy_url),
                description=str(vacancy_payload.get("description") or ""),
                company_name=str(vacancy_payload.get("company_name") or ""),
                questions=list(vacancy_payload.get("questions") or []),
            )
            LOGGER.info("single_cover_letter use_payload_vacancy vacancy=%s elapsed=%.2fs", vacancy_id, time.monotonic() - step_started)
        else:
            step_started = time.monotonic()
            LOGGER.info("single_cover_letter fetch_vacancy start resume_hash=%s vacancy=%s", resume.hash[:10], vacancy_id)
            vacancy = await asyncio.wait_for(
                asyncio.to_thread(fetch_vacancy_data, session, vacancy_id),
                timeout=SINGLE_HH_FETCH_TIMEOUT_SECONDS,
            )
            LOGGER.info("single_cover_letter fetch_vacancy done vacancy=%s elapsed=%.2fs", vacancy_id, time.monotonic() - step_started)
        step_started = time.monotonic()
        LOGGER.info("single_cover_letter generate provider=%s model=%s vacancy=%s", COVER_LETTERS.llm.provider, COVER_LETTERS.llm.model, vacancy_id)
        settings = settings_from_session(session)
        result = await COVER_LETTERS.generate(resume, vacancy, settings, [], force=True)
        LOGGER.info("single_cover_letter generate done vacancy=%s elapsed=%.2fs", vacancy_id, time.monotonic() - step_started)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    except TimeoutError:
        LOGGER.warning("single_cover_letter timeout vacancy=%s timeout=%ss", vacancy_id if "vacancy_id" in locals() else "", SINGLE_HH_FETCH_TIMEOUT_SECONDS)
        raise HTTPException(status_code=504, detail="Timeout while loading data from HH")
    except LlmError as exc:
        LOGGER.warning("single_cover_letter llm_error vacancy=%s code=%s", vacancy_id if "vacancy_id" in locals() else "", exc.code)
        raise HTTPException(status_code=502, detail=f"Не удалось сгенерировать письмо: {exc.code}")
    except Exception as exc:
        LOGGER.warning("single_cover_letter error vacancy=%s error=%s", vacancy_id if "vacancy_id" in locals() else "", type(exc).__name__)
        raise HTTPException(status_code=502, detail="Не удалось сгенерировать письмо: LLM_PROVIDER_ERROR")
    LOGGER.info(
        "single_cover_letter status=%s provider=%s model=%s attempts=%s vacancy=%s error=%s",
        result.status,
        result.generation_provider,
        result.generation_model,
        result.generation_attempts,
        vacancy.id,
        result.generation_error,
    )
    LOGGER.info("single_cover_letter done vacancy=%s total_elapsed=%.2fs", vacancy.id, time.monotonic() - request_started)
    return {
        "ok": result.status == "GENERATED",
        "status": result.status,
        "cover_letter": result.cover_letter,
        "match_analysis": result.match_analysis,
        "generation_model": result.generation_model,
        "generation_provider": result.generation_provider,
        "prompt_version": result.prompt_version,
        "generation_attempts": result.generation_attempts,
        "generation_error": result.generation_error,
    }


@router.post("/api/single/apply")
async def single_apply(payload: dict):
    session = load_session()
    if not session.cookies:
        raise HTTPException(status_code=400, detail="Сначала импортируйте cURL на главной странице")
    if not session.selected_resume_hash:
        raise HTTPException(status_code=400, detail="Сначала выберите резюме на главной странице")
    cover_letter = str(payload.get("cover_letter") or "").strip()
    if not cover_letter:
        raise HTTPException(status_code=400, detail="Письмо пустое")
    try:
        vacancy_id = _vacancy_id_from_url(str(payload.get("vacancy_url") or ""))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))
    result, info = send_apply(session, vacancy_id, cover_letter)
    return {"ok": result in {"sent", "already"}, "result": result, "info": info, "vacancy_id": vacancy_id}


@router.post("/api/run/start")
async def run_start(payload: dict):
    session = load_session()
    snapshot_id = str(payload.get("snapshot_id") or "")
    snap = SNAPSHOTS.get(snapshot_id)
    if not snap:
        raise HTTPException(status_code=400, detail="Unknown snapshot_id")
    dry_run = not bool(payload.get("allow_real_apply", False))
    mode = str(payload.get("cover_letter_mode") or session.cover_letter_mode or "common")
    if mode == "personal" and not dry_run:
        entries = SNAPSHOTS.cover_letter_entries(snapshot_id)
        pending = [e for e in entries if e.get("coverLetterStatus") in {"PENDING", "GENERATING"}]
        failed_or_empty = [e for e in entries if e.get("coverLetterStatus") in {"FAILED", "SKIPPED"} or not e.get("coverLetter")]
        allow_fallback = bool(payload.get("allow_apply_without_cover_letter", session.cover_letter_allow_empty_fallback))
        if pending:
            raise HTTPException(status_code=409, detail="Cover letter generation is not finished")
        if failed_or_empty and not allow_fallback:
            raise HTTPException(status_code=409, detail="Some vacancies have no generated cover letter")
    config = RunConfig(
        resume_hash=session.selected_resume_hash,
        cover_letter=str(payload.get("cover_letter") if payload.get("cover_letter") is not None else session.cover_letter),
        search_url=snap["search_url"],
        pages=max((v.source_page for v in snap["vacancies"]), default=0) + 1,
        delay_seconds=max(0.0, float(payload.get("delay_seconds") if payload.get("delay_seconds") is not None else session.delay_seconds)),
        dry_run=dry_run,
        cover_letter_mode="personal" if mode == "personal" else "common",
        allow_apply_without_cover_letter=bool(payload.get("allow_apply_without_cover_letter", session.cover_letter_allow_empty_fallback)),
        max_applications=max(0, int(payload.get("max_applications") if payload.get("max_applications") is not None else session.max_applications)),
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
