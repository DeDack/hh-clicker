from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime
from typing import Literal


@dataclass(frozen=True)
class SearchVacancy:
    id: str
    url: str
    title: str
    source_search_url: str
    source_page: int

    def __post_init__(self) -> None:
        if not self.id or not self.id.isdigit():
            raise ValueError("vacancy id must be numeric")
        expected = f"https://hh.ru/vacancy/{self.id}"
        if self.url != expected:
            raise ValueError("vacancy url must be canonical")
        if not self.title.strip():
            raise ValueError("vacancy title is required")
        if not self.source_search_url:
            raise ValueError("source_search_url is required")
        if self.source_page < 0:
            raise ValueError("source_page must be >= 0")

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass(frozen=True)
class RunConfig:
    resume_hash: str
    cover_letter: str
    search_url: str
    pages: int
    delay_seconds: float
    dry_run: bool = True
    cover_letter_mode: Literal["common", "personal"] = "common"
    allow_apply_without_cover_letter: bool = False
    max_applications: int = 0


@dataclass
class WorkerState:
    status: Literal["idle", "running", "stopping", "stopped", "done", "error"] = "idle"
    stop_requested: bool = False
    collected: int = 0
    processed: int = 0
    applied: int = 0
    already: int = 0
    skipped: int = 0
    failed: int = 0
    current_vacancy: str = ""
    snapshot_id: str = ""
    logs: list[str] = field(default_factory=list)

    def log(self, message: str) -> None:
        ts = datetime.now().strftime("%H:%M:%S")
        self.logs.append(f"{ts} {message}")
        self.logs = self.logs[-500:]

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class SessionData:
    cookies: dict
    headers: dict
    resumes: list[dict] = field(default_factory=list)
    selected_resume_hash: str = ""
    cover_letter: str = ""
    last_search_url: str = ""
    last_recommendation_url: str = ""
    recommendation_keyword: str = ""
    recommendation_exclude_keywords: str = ""
    pages: int = 1
    delay_seconds: float = 1.0
    max_applications: int = 0
    cover_letter_mode: Literal["common", "personal"] = "personal"
    cover_letter_style: str = "живой"
    cover_letter_length: str = "среднее"
    cover_letter_use_company: bool = True
    cover_letter_use_vacancy_title: bool = True
    cover_letter_auto_generate: bool = True
    cover_letter_allow_empty_fallback: bool = False
    cover_letter_max_attempts: int = 2

    def public(self) -> dict:
        return {
            "has_session": bool(self.cookies),
            "user_agent": self.headers.get("User-Agent", ""),
            "resumes": self.resumes,
            "selected_resume_hash": self.selected_resume_hash,
            "cover_letter": self.cover_letter,
            "last_search_url": self.last_search_url,
            "last_recommendation_url": self.last_recommendation_url,
            "recommendation_keyword": self.recommendation_keyword,
            "recommendation_exclude_keywords": self.recommendation_exclude_keywords,
            "pages": self.pages,
            "delay_seconds": self.delay_seconds,
            "max_applications": self.max_applications,
            "cover_letter_mode": self.cover_letter_mode,
            "cover_letter_style": self.cover_letter_style,
            "cover_letter_length": self.cover_letter_length,
            "cover_letter_use_company": self.cover_letter_use_company,
            "cover_letter_use_vacancy_title": self.cover_letter_use_vacancy_title,
            "cover_letter_auto_generate": self.cover_letter_auto_generate,
            "cover_letter_allow_empty_fallback": self.cover_letter_allow_empty_fallback,
            "cover_letter_max_attempts": self.cover_letter_max_attempts,
        }


@dataclass(frozen=True)
class ResumeData:
    id: str
    title: str
    text: str
    hash: str


@dataclass(frozen=True)
class VacancyData:
    id: str
    title: str
    url: str
    description: str
    company_name: str = ""
    questions: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class CoverLetterSettings:
    mode: Literal["common", "personal"] = "personal"
    style: str = "живой"
    length: str = "среднее"
    use_company: bool = True
    use_vacancy_title: bool = True
    auto_generate: bool = True
    allow_empty_fallback: bool = False
    max_attempts: int = 2
    model: str = ""
    allowed_technologies: tuple[str, ...] = ()


@dataclass(frozen=True)
class CoverLetterResult:
    match_analysis: dict
    cover_letter: str
    status: Literal["PENDING", "GENERATING", "GENERATED", "EDITED", "FAILED", "SKIPPED"]
    generated_at: str
    generation_model: str
    generation_attempts: int
    generation_error: str | None = None
    generation_provider: str = ""
    prompt_version: str = ""
    input_tokens: int = 0
    output_tokens: int = 0
    estimated_cost: float = 0.0
