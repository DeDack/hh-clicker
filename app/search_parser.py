from __future__ import annotations

import re
from urllib.parse import parse_qsl, urlencode, urlsplit, urlunsplit

from bs4 import BeautifulSoup

from app.models import SearchVacancy


def validate_search_url(url: str) -> str:
    split = urlsplit((url or "").strip())
    if split.scheme != "https":
        raise ValueError("search URL must use https")
    hostname = split.hostname or ""
    if hostname != "hh.ru" and not hostname.endswith(".hh.ru"):
        raise ValueError("search URL host must be hh.ru or an hh.ru subdomain")
    if split.path != "/search/vacancy":
        raise ValueError("search URL path must be /search/vacancy")
    return urlunsplit((split.scheme, split.netloc, split.path, split.query, ""))


def build_page_url(search_url: str, page: int, items_on_page: int | None = None) -> str:
    validate_search_url(search_url)
    split = urlsplit(search_url)
    pairs = [
        (key, value)
        for key, value in parse_qsl(split.query, keep_blank_values=True)
        if key not in {"page", "search_session_id"} and (items_on_page is None or key != "items_on_page")
    ]
    if items_on_page is not None:
        pairs.append(("items_on_page", str(items_on_page)))
    pairs.append(("page", str(page)))
    return urlunsplit((split.scheme, split.netloc, split.path, urlencode(pairs, doseq=True), ""))


def parse_search_vacancies(html: str, source_search_url: str, source_page: int) -> tuple[list[SearchVacancy], dict]:
    soup = BeautifulSoup(html or "", "html.parser")
    container = soup.select_one('[data-qa="vacancy-serp__results"]')
    if container is None:
        return [], {"cards": 0, "accepted": 0, "unique_ids": 0, "missing_container": True}
    cards = container.select('[data-qa="vacancy-serp__vacancy"]')
    vacancies = []
    seen = set()
    for card in cards:
        link = card.select_one('a[data-qa="serp-item__title"]')
        if link is None:
            continue
        href = link.get("href", "") or ""
        match = re.search(r"/vacancy/(\d+)", href)
        if not match:
            continue
        vacancy_id = match.group(1)
        if vacancy_id in seen:
            continue
        title_el = card.select_one('[data-qa="serp-item__title-text"]')
        title = title_el.get_text(" ", strip=True) if title_el else link.get_text(" ", strip=True)
        if not title:
            continue
        search_text = re.sub(r"\s+", " ", card.get_text(" ", strip=True)).strip()
        seen.add(vacancy_id)
        vacancies.append(SearchVacancy(
            id=vacancy_id,
            url=f"https://hh.ru/vacancy/{vacancy_id}",
            title=title,
            source_search_url=source_search_url,
            source_page=source_page,
            search_text=search_text,
        ))
    return vacancies, {
        "cards": len(cards),
        "accepted": len(vacancies),
        "unique_ids": len(seen),
        "missing_container": False,
    }
