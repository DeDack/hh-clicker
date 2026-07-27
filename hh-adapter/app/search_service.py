from __future__ import annotations

from app.hh_client import HH, sanitize_headers
from app.models import HhSessionPayload, SearchVacancy
from app.search_parser import build_page_url, parse_search_vacancies, validate_search_url


def search_vacancies(session: HhSessionPayload, search_url: str, pages: int, resume_id: str = "") -> tuple[list[SearchVacancy], dict]:
    search_url = validate_search_url(search_url)
    pages = max(1, min(int(pages or 1), 50))
    headers = sanitize_headers({
        key: value
        for key, value in (session.headers or {}).items()
        if key.lower() in {"user-agent", "accept-language", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform"}
    })
    headers.update({
        "User-Agent": headers.get("User-Agent") or "Mozilla/5.0",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Referer": search_url,
    })
    all_vacancies: list[SearchVacancy] = []
    diagnostics = []
    seen: dict[str, SearchVacancy] = {}
    duplicate_count = 0

    for page in range(pages):
        page_url = build_page_url(search_url, page, items_on_page=100)
        response = HH.get(page_url, headers=headers, cookies=session.cookies, timeout=20)
        if response.status_code != 200:
            raise RuntimeError(f"HH search returned HTTP {response.status_code} on page {page}")
        vacancies, diag = parse_search_vacancies(response.text, search_url, page)
        diagnostics.append({"page": page, **diag})
        for vacancy in vacancies:
            if vacancy.id in seen:
                duplicate_count += 1
                continue
            seen[vacancy.id] = vacancy
            all_vacancies.append(vacancy)
        if page > 0 and not vacancies:
            break

    return all_vacancies, {
        "cardsTotal": sum(int(d.get("cards") or 0) for d in diagnostics),
        "acceptedTotal": sum(int(d.get("accepted") or 0) for d in diagnostics),
        "alreadyAppliedTotal": sum(int(d.get("already_applied") or 0) for d in diagnostics),
        "duplicateCount": duplicate_count,
        "resumeIdProvided": bool(resume_id),
        "pages": diagnostics,
    }
