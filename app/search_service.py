from __future__ import annotations

import hashlib
import re
import threading
import time
from dataclasses import asdict
from datetime import datetime, timezone

from app.hh_client import HH
from app.models import CoverLetterResult, SearchVacancy, SessionData
from app.search_parser import build_page_url, parse_search_vacancies, validate_search_url


class SnapshotStore:
    def __init__(self):
        self._lock = threading.RLock()
        self._snapshots: dict[str, dict] = {}

    def create(self, search_url: str, resume_hash: str, vacancies: list[SearchVacancy], metadata: dict | None = None) -> str:
        seed = f"{time.time()}:{resume_hash}:{search_url}:{len(vacancies)}".encode("utf-8")
        snapshot_id = hashlib.sha256(seed).hexdigest()[:16]
        with self._lock:
            cover_letters = {
                v.id: {
                    "vacancyId": v.id,
                    "vacancyTitle": v.title,
                    "companyName": "",
                    "vacancyUrl": v.url,
                    "vacancyDescriptionHash": "",
                    "resumeId": resume_hash,
                    "resumeHash": "",
                    "matchAnalysis": {},
                    "coverLetter": "",
                    "coverLetterStatus": "PENDING",
                    "coverLetterGeneratedAt": "",
                    "coverLetterEditedManually": False,
                    "generationProvider": "",
                    "generationModel": "",
                    "promptVersion": "",
                    "generationAttempts": 0,
                    "generationError": None,
                    "inputTokens": 0,
                    "outputTokens": 0,
                    "estimatedCost": 0.0,
                }
                for v in vacancies
            }
            self._snapshots[snapshot_id] = {
                "search_url": search_url,
                "resume_hash": resume_hash,
                "vacancies": list(vacancies),
                "cover_letters": cover_letters,
                "created_at": time.time(),
                "locked": False,
                "metadata": dict(metadata or {}),
            }
        return snapshot_id

    def lock(self, snapshot_id: str) -> None:
        with self._lock:
            if snapshot_id in self._snapshots:
                self._snapshots[snapshot_id]["locked"] = True

    def remove_vacancy(self, snapshot_id: str, vacancy_id: str) -> dict:
        with self._lock:
            snap = self._snapshots.get(snapshot_id)
            if not snap:
                raise ValueError("unknown snapshot_id")
            if snap.get("locked"):
                raise RuntimeError("snapshot already started")
            before = len(snap["vacancies"])
            snap["vacancies"] = [v for v in snap["vacancies"] if v.id != vacancy_id]
            if len(snap["vacancies"]) == before:
                raise ValueError("unknown vacancy_id")
            snap.setdefault("cover_letters", {}).pop(vacancy_id, None)
        return self.public(snapshot_id)

    def set_cover_letter_status(self, snapshot_id: str, vacancy_id: str, status: str, error: str | None = None) -> None:
        with self._lock:
            entry = self._entry(snapshot_id, vacancy_id)
            entry["coverLetterStatus"] = status
            if error is not None:
                entry["generationError"] = error

    def save_cover_letter_result(
        self,
        snapshot_id: str,
        vacancy_id: str,
        result: CoverLetterResult,
        vacancy_title: str,
        company_name: str,
        vacancy_description_hash: str,
        resume_hash: str,
    ) -> dict:
        with self._lock:
            entry = self._entry(snapshot_id, vacancy_id)
            entry.update({
                "vacancyTitle": vacancy_title,
                "companyName": company_name,
                "vacancyDescriptionHash": vacancy_description_hash,
                "resumeHash": resume_hash,
                "matchAnalysis": result.match_analysis,
                "coverLetter": result.cover_letter,
                "coverLetterStatus": result.status,
                "coverLetterGeneratedAt": result.generated_at,
                "coverLetterEditedManually": False,
                "generationProvider": result.generation_provider,
                "generationModel": result.generation_model,
                "promptVersion": result.prompt_version,
                "generationAttempts": result.generation_attempts,
                "generationError": result.generation_error,
                "inputTokens": result.input_tokens,
                "outputTokens": result.output_tokens,
                "estimatedCost": result.estimated_cost,
            })
            return dict(entry)

    def update_cover_letter(self, snapshot_id: str, vacancy_id: str, text: str) -> dict:
        with self._lock:
            snap = self._snapshots.get(snapshot_id)
            if not snap:
                raise ValueError("unknown snapshot_id")
            if snap.get("locked"):
                raise RuntimeError("snapshot already started")
            entry = self._entry(snapshot_id, vacancy_id)
            entry["coverLetter"] = text.strip()
            entry["coverLetterStatus"] = "EDITED"
            entry["coverLetterEditedManually"] = True
            entry["coverLetterGeneratedAt"] = datetime.now(timezone.utc).isoformat()
            entry["generationError"] = None
            return dict(entry)

    def get_cover_letter(self, snapshot_id: str, vacancy_id: str) -> dict | None:
        with self._lock:
            snap = self._snapshots.get(snapshot_id)
            if not snap:
                return None
            entry = snap.setdefault("cover_letters", {}).get(vacancy_id)
            return dict(entry) if entry else None

    def cover_letter_entries(self, snapshot_id: str) -> list[dict]:
        with self._lock:
            snap = self._snapshots.get(snapshot_id)
            if not snap:
                return []
            vacancies = {v.id for v in snap["vacancies"]}
            return [dict(v) for k, v in snap.setdefault("cover_letters", {}).items() if k in vacancies]

    def _entry(self, snapshot_id: str, vacancy_id: str) -> dict:
        snap = self._snapshots.get(snapshot_id)
        if not snap:
            raise ValueError("unknown snapshot_id")
        entry = snap.setdefault("cover_letters", {}).get(vacancy_id)
        if not entry:
            raise ValueError("unknown vacancy_id")
        return entry

    def get(self, snapshot_id: str) -> dict | None:
        with self._lock:
            snap = self._snapshots.get(snapshot_id)
            if not snap:
                return None
            return {**snap, "vacancies": list(snap["vacancies"]), "cover_letters": {k: dict(v) for k, v in snap.get("cover_letters", {}).items()}}

    def public(self, snapshot_id: str) -> dict:
        snap = self.get(snapshot_id)
        if not snap:
            return {}
        return {
            "snapshot_id": snapshot_id,
            "count": len(snap["vacancies"]),
            "resume_hash": snap["resume_hash"],
            "locked": bool(snap.get("locked")),
            **(snap.get("metadata") if isinstance(snap.get("metadata"), dict) else {}),
            "vacancies": [
                {**asdict(v), "coverLetter": snap.get("cover_letters", {}).get(v.id, {})}
                for v in snap["vacancies"]
            ],
            "cover_letters": list(snap.get("cover_letters", {}).values()),
        }


SNAPSHOTS = SnapshotStore()


def _normalize_title_text(text: str) -> tuple[str, str]:
    spaced = re.sub(r"[\W_]+", " ", (text or "").casefold())
    spaced = re.sub(r"\s+", " ", spaced).strip()
    compact = spaced.replace(" ", "")
    return spaced, compact


def _keyword_match(title: str, keyword: str) -> bool:
    keyword_spaced, keyword_compact = _normalize_title_text(keyword)
    if not keyword_spaced:
        return True
    title_spaced, title_compact = _normalize_title_text(title)
    return keyword_spaced in title_spaced or keyword_compact in title_compact


def _title_matches_keyword(title: str, keyword: str) -> bool:
    return _keyword_match(title, keyword)


def _split_keywords(raw: str) -> list[str]:
    return [part.strip().casefold() for part in (raw or "").split(",") if part.strip()]


def _title_excluded_by_keyword(title: str, keywords: list[str]) -> str:
    title_spaced, title_compact = _normalize_title_text(title)
    for keyword in keywords:
        keyword_spaced, keyword_compact = _normalize_title_text(keyword)
        if keyword_spaced and (keyword_spaced in title_spaced or keyword_compact in title_compact):
            return keyword
    return ""


def preview_search(
    session: SessionData,
    search_url: str,
    pages: int,
    title_keyword: str = "",
    exclude_title_keywords: str = "",
) -> dict:
    search_url = validate_search_url(search_url)
    pages = max(1, min(int(pages or 1), 50))
    title_keyword = (title_keyword or "").strip()
    exclude_keywords = _split_keywords(exclude_title_keywords)
    headers = {
        "User-Agent": session.headers.get("User-Agent") or "Mozilla/5.0",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer": "https://hh.ru/",
    }
    all_vacancies = []
    diagnostics = []
    seen: dict[str, SearchVacancy] = {}
    duplicate_count = 0
    duplicate_examples = []
    filtered_by_title = 0
    excluded_by_title = 0
    excluded_vacancies = []
    for page in range(pages):
        page_url = build_page_url(search_url, page, items_on_page=20)
        response = HH.get(page_url, headers=headers, cookies=session.cookies, timeout=20)
        if response.status_code != 200:
            raise RuntimeError(f"HH search returned HTTP {response.status_code} on page {page}")
        vacancies, diag = parse_search_vacancies(response.text, search_url, page)
        diagnostics.append({"page": page, **diag})
        for vacancy in vacancies:
            if not _title_matches_keyword(vacancy.title, title_keyword):
                filtered_by_title += 1
                continue
            excluded_word = _title_excluded_by_keyword(vacancy.title, exclude_keywords)
            if excluded_word:
                excluded_by_title += 1
                excluded_vacancies.append({
                    "id": vacancy.id,
                    "title": vacancy.title,
                    "source_page": vacancy.source_page,
                    "reason": f"excluded_word={excluded_word}",
                    "excluded_word": excluded_word,
                })
                continue
            if vacancy.id in seen:
                duplicate_count += 1
                if len(duplicate_examples) < 20:
                    first = seen[vacancy.id]
                    duplicate_examples.append({
                        "id": vacancy.id,
                        "title": vacancy.title,
                        "first_page": first.source_page,
                        "duplicate_page": vacancy.source_page,
                    })
                continue
            seen[vacancy.id] = vacancy
            all_vacancies.append(vacancy)
        if page > 0 and not vacancies:
            break
    cards_total = sum(int(d.get("cards") or 0) for d in diagnostics)
    accepted_total = sum(int(d.get("accepted") or 0) for d in diagnostics)
    metadata = {
        "cards_total": cards_total,
        "accepted_total": accepted_total,
        "duplicate_count": duplicate_count,
        "duplicate_examples": duplicate_examples,
        "title_keyword": title_keyword,
        "exclude_title_keywords": exclude_keywords,
        "filtered_by_title": filtered_by_title,
        "excluded_by_title": excluded_by_title,
        "excluded_vacancies": excluded_vacancies,
        "diagnostics": diagnostics,
    }
    snapshot_id = SNAPSHOTS.create(search_url, session.selected_resume_hash, all_vacancies, metadata)
    return SNAPSHOTS.public(snapshot_id)
