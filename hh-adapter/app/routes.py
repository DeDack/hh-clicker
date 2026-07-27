from __future__ import annotations

import logging
import uuid

from fastapi import APIRouter, Depends, Request
from fastapi.responses import JSONResponse

from app.apply_service import send_apply
from app.auth import require_internal_api_key
from app.cover_letter_service import CoverLetterGenerationService, fetch_resume_data, fetch_vacancy_data, sha256_text
from app.curl_parser import parse_curl
from app.llm_client import LlmError
from app.models import (
    ApplyRequest,
    ApplyResponse,
    CoverLetterSettings,
    GenerateCoverLetterRequest,
    GenerateCoverLetterResponse,
    ListResumesRequest,
    ListResumesResponse,
    LoadResumeRequest,
    LoadResumeResponse,
    LoadVacancyRequest,
    LoadVacancyResponse,
    ParseCurlRequest,
    ParseCurlResponse,
    ResumeData,
    ResumeSummary,
    SearchVacanciesRequest,
    SearchVacanciesResponse,
    VacancyData,
    VacancySummary,
    ValidateSessionRequest,
    ValidateSessionResponse,
)
from app.resume_service import fetch_resumes
from app.search_service import search_vacancies


LOGGER = logging.getLogger("uvicorn.error")
router = APIRouter()
internal_router = APIRouter(prefix="/internal/v1", dependencies=[Depends(require_internal_api_key)])
COVER_LETTERS = CoverLetterGenerationService()


@router.get("/health")
async def health() -> dict:
    return {"status": "ok"}


@internal_router.post("/curl/parse", response_model=ParseCurlResponse)
async def parse_curl_endpoint(payload: ParseCurlRequest) -> ParseCurlResponse:
    parsed = parse_curl(payload.rawCurl)
    return ParseCurlResponse(
        url=parsed.url,
        cookies=parsed.cookies,
        headers=parsed.headers,
        cookiesCount=len(parsed.cookies),
        hasHhToken=bool(parsed.cookies.get("hhtoken")),
        hasXsrf=bool(parsed.cookies.get("_xsrf") or parsed.headers.get("X-XSRFToken")),
    )


@internal_router.post("/hh/session/validate", response_model=ValidateSessionResponse)
async def validate_session(payload: ValidateSessionRequest) -> ValidateSessionResponse:
    ok, message, resumes = fetch_resumes(payload.session)
    return ValidateSessionResponse(
        valid=ok,
        status="ACTIVE" if ok else "INVALID",
        message=message,
        resumes=[ResumeSummary(hhResumeId=str(item.get("hash") or ""), title=str(item.get("title") or "Резюме")) for item in resumes],
    )


@internal_router.post("/hh/resumes/list", response_model=ListResumesResponse)
async def list_resumes(payload: ListResumesRequest) -> ListResumesResponse:
    ok, message, resumes = fetch_resumes(payload.session)
    if not ok:
        raise RuntimeError(message)
    return ListResumesResponse(
        resumes=[ResumeSummary(hhResumeId=str(item.get("hash") or ""), title=str(item.get("title") or "Резюме")) for item in resumes]
    )


@internal_router.post("/hh/resumes/load", response_model=LoadResumeResponse)
async def load_resume(payload: LoadResumeRequest) -> LoadResumeResponse:
    resume = fetch_resume_data(payload.session, payload.resumeId, payload.title)
    return LoadResumeResponse(hhResumeId=resume.id, title=resume.title, text=resume.text, contentHash=resume.hash, gender=resume.gender)


@internal_router.post("/hh/vacancies/search", response_model=SearchVacanciesResponse)
async def vacancies_search(payload: SearchVacanciesRequest) -> SearchVacanciesResponse:
    vacancies, diagnostics = search_vacancies(payload.session, payload.searchUrl, payload.pages, payload.resumeId)
    return SearchVacanciesResponse(
        vacancies=[
            VacancySummary(
                hhVacancyId=v.id,
                url=v.url,
                title=v.title,
                searchText=v.search_text,
                sourcePage=v.source_page,
                alreadyApplied=v.already_applied,
                applicationState=v.application_state,
                applicationStateSource=v.application_state_source,
            )
            for v in vacancies
        ],
        diagnostics=diagnostics,
    )


@internal_router.post("/hh/vacancies/load", response_model=LoadVacancyResponse)
async def load_vacancy(payload: LoadVacancyRequest) -> LoadVacancyResponse:
    vacancy = fetch_vacancy_data(payload.session, payload.vacancyId, payload.title)
    return LoadVacancyResponse(
        hhVacancyId=vacancy.id,
        title=vacancy.title,
        companyName=vacancy.company_name,
        url=vacancy.url,
        description=vacancy.description,
        descriptionHash=sha256_text(vacancy.description),
        questions=vacancy.questions,
    )


@internal_router.post("/hh/applications/apply", response_model=ApplyResponse)
async def apply(payload: ApplyRequest) -> ApplyResponse:
    status, info = send_apply(payload.session, payload.resumeId, payload.vacancyId, payload.coverLetter)
    mapped = {
        "sent": "SENT",
        "already": "ALREADY_APPLIED",
        "test": "TEST_REQUIRED",
        "limit": "LIMIT_EXCEEDED",
        "auth_error": "AUTH_ERROR",
        "error": "FAILED",
    }.get(status, "FAILED")
    return ApplyResponse(
        status=mapped,
        httpStatus=info.get("http_status"),
        topicId=info.get("topic_id") or None,
        chatId=info.get("chat_id") or None,
        errorCode=None if mapped == "SENT" else mapped,
    )


@internal_router.post("/cover-letters/generate", response_model=GenerateCoverLetterResponse)
async def generate_cover_letter(payload: GenerateCoverLetterRequest) -> GenerateCoverLetterResponse:
    resume_text = "\n\n".join(part for part in (payload.resume.text, payload.candidateProfile) if part.strip())
    resume_hash = payload.resume.contentHash or sha256_text(resume_text)
    resume = ResumeData(
        id="",
        title=payload.resume.title or "Резюме",
        text=resume_text,
        hash=resume_hash,
        gender=payload.candidateGender,
        telegram_username=payload.telegramUsername,
    )
    vacancy = VacancyData(
        id=payload.vacancy.hhVacancyId,
        title=payload.vacancy.title,
        url=f"https://hh.ru/vacancy/{payload.vacancy.hhVacancyId}",
        company_name=payload.vacancy.companyName,
        description=payload.vacancy.description,
        questions=payload.vacancy.questions,
    )
    settings = CoverLetterSettings(
        style=payload.settings.style,
        use_company=payload.settings.useCompany,
        use_vacancy_title=payload.settings.useVacancyTitle,
        max_attempts=max(1, min(payload.settings.maxAttempts, 5)),
    )
    result = await COVER_LETTERS.generate(resume, vacancy, settings)
    status = "PROFILE_MISMATCH" if result.generation_error == "NONMATCH" else result.status
    if status not in {"GENERATED", "FAILED", "PROFILE_MISMATCH"}:
        status = "FAILED"
    return GenerateCoverLetterResponse(
        status=status,
        coverLetter=result.cover_letter,
        matchAnalysis=result.match_analysis,
        provider=result.generation_provider,
        model=result.generation_model,
        promptVersion=result.prompt_version,
        inputTokens=result.input_tokens,
        outputTokens=result.output_tokens,
        attempts=result.generation_attempts,
        errorCode=result.generation_error,
    )


@internal_router.get("/llm/status")
async def llm_status() -> dict:
    check = getattr(COVER_LETTERS.llm, "check_status", None)
    if not check:
        return {"configured": True, "provider": COVER_LETTERS.llm.provider, "reachable": True, "model": COVER_LETTERS.llm.model}
    return await check()


async def safe_error_handler(request: Request, exc: Exception) -> JSONResponse:
    request_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
    code = getattr(exc, "code", "ADAPTER_ERROR")
    status = 502
    if isinstance(exc, LlmError):
        status = 503 if getattr(exc, "retryable", False) else 400
    LOGGER.warning("adapter_error request_id=%s code=%s path=%s", request_id, code, request.url.path)
    return JSONResponse(
        status_code=status,
        content={"code": code, "message": "Adapter operation failed", "requestId": request_id},
        headers={"X-Request-Id": request_id},
    )
