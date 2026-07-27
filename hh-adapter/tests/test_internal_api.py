from fastapi.testclient import TestClient

from app.apply_service import classify_apply_response
import app.routes as routes
from app.cover_letter_service import _map_hh_gender
from app.llm_client import LlmBadResponseError, extract_anthropic_text
from app.models import CoverLetterResult
from app.main import app


def test_internal_api_requires_key(monkeypatch):
    monkeypatch.setenv("HH_ADAPTER_API_KEY", "secret")
    client = TestClient(app)

    response = client.post("/internal/v1/curl/parse", json={"rawCurl": "cookie: hhtoken=a; _xsrf=b"})

    assert response.status_code == 401


def test_parse_curl_preserves_cookies(monkeypatch):
    monkeypatch.setenv("HH_ADAPTER_API_KEY", "secret")
    client = TestClient(app)

    response = client.post(
        "/internal/v1/curl/parse",
        headers={"X-Internal-Api-Key": "secret"},
        json={"rawCurl": "cookie: hhtoken=a; _xsrf=b; unrelated=c"},
    )

    assert response.status_code == 200
    payload = response.json()
    assert payload["cookies"] == {"hhtoken": "a", "_xsrf": "b", "unrelated": "c"}
    assert payload["cookiesCount"] == 3
    assert payload["hasHhToken"] is True
    assert payload["hasXsrf"] is True


def test_apply_response_classification():
    assert classify_apply_response(200, '{"success":true,"topic_id":"1"}')[0] == "sent"
    assert classify_apply_response(200, '{"responseStatus":{"alreadyApplied":true}}')[0] == "already"
    assert classify_apply_response(200, '{"error":"test-required"}')[0] == "test"
    assert classify_apply_response(200, '{"error":"negotiations-limit-exceeded"}')[0] == "limit"
    assert classify_apply_response(401, "")[0] == "auth_error"


def test_anthropic_text_blocks():
    payload = {
        "content": [
            {"type": "tool_use", "name": "ignored"},
            {"type": "text", "text": "Первый блок"},
            {"type": "text", "text": "Второй блок"},
        ]
    }

    assert extract_anthropic_text(payload) == "Первый блок\nВторой блок"


def test_anthropic_empty_content_is_bad_response():
    try:
        extract_anthropic_text({"content": []})
    except LlmBadResponseError as exc:
        assert exc.code == "LLM_BAD_RESPONSE"
    else:
        raise AssertionError("expected LlmBadResponseError")


def test_adapter_has_no_stateful_storage_imports():
    forbidden = ("storage", "SessionData", "SnapshotStore", "SNAPSHOTS", "ApplyWorker", "RunConfig", "WorkerState")
    for path in __import__("pathlib").Path("app").rglob("*.py"):
        text = path.read_text(encoding="utf-8")
        assert not any(marker in text for marker in forbidden), path


def test_hh_resume_gender_mapping_uses_gender_id_only():
    assert _map_hh_gender({"id": "male", "name": "Мужчина"}) == "MALE"
    assert _map_hh_gender({"id": "female", "name": "Женщина"}) == "FEMALE"
    assert _map_hh_gender({"id": "other", "name": "Other"}) == "UNKNOWN"
    assert _map_hh_gender(None) == "UNKNOWN"


def test_generate_request_passes_gender_and_telegram(monkeypatch):
    class FakeGenerator:
        async def generate(self, resume, vacancy, settings):
            assert resume.gender == "FEMALE"
            assert resume.telegram_username == "masabi19"
            assert "candidate profile" in resume.text
            return CoverLetterResult(
                {},
                "Проектировала интеграции для 1С. Мой тг @masabi19.",
                "GENERATED",
                "now",
                "fake",
                1,
                None,
                "fake",
                "v3-gender-strict",
                1,
                1,
            )

    monkeypatch.setenv("HH_ADAPTER_API_KEY", "secret")
    monkeypatch.setattr(routes, "COVER_LETTERS", FakeGenerator())
    client = TestClient(app)

    response = client.post(
        "/internal/v1/cover-letters/generate",
        headers={"X-Internal-Api-Key": "secret"},
        json={
            "resume": {"title": "Аналитик 1С", "text": "resume text", "contentHash": "hash"},
            "candidateProfile": "candidate profile",
            "candidateGender": "FEMALE",
            "telegramUsername": "masabi19",
            "vacancy": {"hhVacancyId": "1", "title": "Аналитик 1С", "companyName": "Company", "description": "1С"},
            "settings": {"style": "живой", "useCompany": True, "useVacancyTitle": True, "maxAttempts": 2},
        },
    )

    assert response.status_code == 200
    assert response.json()["promptVersion"] == "v3-gender-strict"
