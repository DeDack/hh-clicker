from __future__ import annotations

from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any, Literal

from pydantic import BaseModel, Field


class HhSessionPayload(BaseModel):
    cookies: dict[str, str] = Field(default_factory=dict)
    headers: dict[str, str] = Field(default_factory=dict)


class ParseCurlRequest(BaseModel):
    rawCurl: str


class ParseCurlResponse(BaseModel):
    url: str = ""
    cookies: dict[str, str] = Field(default_factory=dict)
    headers: dict[str, str] = Field(default_factory=dict)
    cookiesCount: int = 0
    hasHhToken: bool = False
    hasXsrf: bool = False


class ValidateSessionRequest(BaseModel):
    session: HhSessionPayload


class ResumeSummary(BaseModel):
    hhResumeId: str
    title: str


class ValidateSessionResponse(BaseModel):
    valid: bool
    status: Literal["ACTIVE", "EXPIRED", "INVALID", "UNKNOWN"]
    message: str
    resumes: list[ResumeSummary] = Field(default_factory=list)


class ListResumesRequest(BaseModel):
    session: HhSessionPayload


class ListResumesResponse(BaseModel):
    resumes: list[ResumeSummary] = Field(default_factory=list)


class LoadResumeRequest(BaseModel):
    session: HhSessionPayload
    resumeId: str
    title: str = ""


class LoadResumeResponse(BaseModel):
    hhResumeId: str
    title: str
    text: str
    contentHash: str
    gender: Literal["MALE", "FEMALE", "UNKNOWN"] = "UNKNOWN"


class SearchVacanciesRequest(BaseModel):
    session: HhSessionPayload
    searchUrl: str
    pages: int = 1
    resumeId: str = ""


class VacancySummary(BaseModel):
    hhVacancyId: str
    url: str
    title: str
    searchText: str = ""
    sourcePage: int
    alreadyApplied: bool = False
    applicationState: Literal["NOT_APPLIED", "ALREADY_APPLIED", "UNKNOWN"] = "UNKNOWN"
    applicationStateSource: str = ""


class SearchVacanciesResponse(BaseModel):
    vacancies: list[VacancySummary] = Field(default_factory=list)
    diagnostics: dict[str, Any] = Field(default_factory=dict)


class LoadVacancyRequest(BaseModel):
    session: HhSessionPayload
    vacancyId: str
    title: str = ""


class LoadVacancyResponse(BaseModel):
    hhVacancyId: str
    title: str
    companyName: str = ""
    url: str
    description: str
    descriptionHash: str
    questions: list[str] = Field(default_factory=list)


class ApplyRequest(BaseModel):
    session: HhSessionPayload
    resumeId: str
    vacancyId: str
    coverLetter: str = ""


class ApplyResponse(BaseModel):
    status: Literal["SENT", "ALREADY_APPLIED", "TEST_REQUIRED", "LIMIT_EXCEEDED", "AUTH_ERROR", "FAILED"]
    httpStatus: int | None = None
    topicId: str | None = None
    chatId: str | None = None
    errorCode: str | None = None


class GenerateResumePayload(BaseModel):
    title: str = ""
    text: str
    contentHash: str = ""


class GenerateVacancyPayload(BaseModel):
    hhVacancyId: str
    title: str
    companyName: str = ""
    description: str = ""
    questions: list[str] = Field(default_factory=list)


class GenerateCoverLetterSettings(BaseModel):
    style: str = "живой"
    useCompany: bool = True
    useVacancyTitle: bool = True
    maxAttempts: int = 2


class GenerateCoverLetterRequest(BaseModel):
    resume: GenerateResumePayload
    candidateProfile: str = ""
    candidateGender: Literal["MALE", "FEMALE", "UNKNOWN"] = "UNKNOWN"
    telegramUsername: str = ""
    vacancy: GenerateVacancyPayload
    settings: GenerateCoverLetterSettings = Field(default_factory=GenerateCoverLetterSettings)


class GenerateCoverLetterResponse(BaseModel):
    status: Literal["GENERATED", "FAILED", "PROFILE_MISMATCH"]
    coverLetter: str = ""
    matchAnalysis: dict[str, Any] = Field(default_factory=dict)
    provider: str = ""
    model: str = ""
    promptVersion: str = ""
    inputTokens: int = 0
    outputTokens: int = 0
    attempts: int = 0
    errorCode: str | None = None


@dataclass(frozen=True)
class SearchVacancy:
    id: str
    url: str
    title: str
    source_search_url: str
    source_page: int
    search_text: str = ""
    already_applied: bool = False
    application_state: str = "UNKNOWN"
    application_state_source: str = ""

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
class ResumeData:
    id: str
    title: str
    text: str
    hash: str
    gender: Literal["MALE", "FEMALE", "UNKNOWN"] = "UNKNOWN"
    telegram_username: str = ""


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


def now_utc() -> str:
    return datetime.now(timezone.utc).isoformat()
