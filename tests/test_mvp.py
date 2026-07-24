import time

import pytest
from fastapi.testclient import TestClient

from app.apply_service import classify_apply_response
from app.curl_parser import parse_curl
from app.main import app
from app.models import RunConfig, SearchVacancy, SessionData
from app.search_parser import build_page_url, parse_search_vacancies, validate_search_url
from app.search_service import SNAPSHOTS, preview_search
from app.worker import ApplyWorker
from app import routes


SEARCH_URL = "https://hh.ru/search/vacancy?text=golang&search_field=name&search_field=description&search_session_id=drop&page=8"


def sample_html():
    return """
    <a data-qa="serp-item__title" href="/vacancy/999999999">outside</a>
    <div data-qa="vacancy-serp__results">
      <div data-qa="vacancy-serp__vacancy">
        <a data-qa="serp-item__title" href="https://hh.ru/vacancy/111?x=1">
          <span data-qa="serp-item__title-text">Go developer</span>
        </a>
        <a href="/vacancy/777">secondary ignored</a>
      </div>
      <div data-qa="vacancy-serp__vacancy">
        <a data-qa="serp-item__title" href="/vacancy/222">Python developer</a>
      </div>
      <div data-qa="vacancy-serp__vacancy">
        <a data-qa="serp-item__title" href="/vacancy/111">Duplicate</a>
      </div>
    </div>
    """


class FakeResponse:
    def __init__(self, text, status_code=200):
        self.text = text
        self.status_code = status_code


def test_curl_parser_public_does_not_return_cookies():
    parsed = parse_curl("curl 'https://hh.ru/applicant/resumes' -H 'User-Agent: UA' -H 'Cookie: hhtoken=abc; _xsrf=xs; tracking=no'")
    public = parsed.public()
    assert public["has_hhtoken"] is True
    assert "cookies" not in public
    assert parsed.cookies == {"hhtoken": "abc", "_xsrf": "xs"}


def test_session_check_without_cookies_returns_not_alive(monkeypatch, tmp_path):
    monkeypatch.setattr("app.storage.DATA_DIR", tmp_path)
    monkeypatch.setattr("app.storage.SETTINGS_FILE", tmp_path / "settings.json")
    monkeypatch.setattr("app.storage.APPLICATIONS_FILE", tmp_path / "applications.json")
    monkeypatch.setattr(routes, "load_session", __import__("app.storage", fromlist=["load_session"]).load_session)
    client = TestClient(app)
    response = client.get("/api/session/check")
    data = response.json()
    assert response.status_code == 200
    assert data["alive"] is False
    assert "cookies" not in data["session"]


def test_session_check_alive_does_not_return_cookies(monkeypatch, tmp_path):
    monkeypatch.setattr("app.storage.DATA_DIR", tmp_path)
    monkeypatch.setattr("app.storage.SETTINGS_FILE", tmp_path / "settings.json")
    monkeypatch.setattr("app.storage.APPLICATIONS_FILE", tmp_path / "applications.json")
    from app.storage import save_session, load_session

    save_session(SessionData(cookies={"hhtoken": "secret"}, headers={"User-Agent": "UA"}))
    monkeypatch.setattr(routes, "load_session", load_session)
    monkeypatch.setattr(routes, "save_session", save_session)
    monkeypatch.setattr(routes, "fetch_resumes", lambda session: (True, "Сессия подтверждена", [{"hash": "a" * 20, "title": "Resume"}]))
    client = TestClient(app)
    response = client.get("/api/session/check")
    data = response.json()
    assert response.status_code == 200
    assert data["alive"] is True
    assert "cookies" not in data["session"]
    assert data["session"]["resumes"][0]["title"] == "Resume"


def test_url_validation_rejects_other_domain():
    with pytest.raises(ValueError):
        validate_search_url("https://evil.example/search/vacancy?text=go")


def test_url_validation_accepts_hh_subdomain():
    url = "https://kazan.hh.ru/search/vacancy?resume=122841afff0d8234980039ed1f50484f433753&hhtmFrom=main"
    assert validate_search_url(url) == url
    assert build_page_url(url, 1).startswith("https://kazan.hh.ru/search/vacancy?")


def test_parser_ignores_outside_links_and_dedupes():
    vacancies, diag = parse_search_vacancies(sample_html(), SEARCH_URL, 0)
    assert diag["cards"] == 3
    assert [v.id for v in vacancies] == ["111", "222"]
    assert "999999999" not in {v.id for v in vacancies}
    assert all(v.url == f"https://hh.ru/vacancy/{v.id}" for v in vacancies)


def test_pagination_preserves_repeated_search_field():
    url = build_page_url(SEARCH_URL, 3)
    assert "search_field=name" in url
    assert "search_field=description" in url
    assert "search_session_id" not in url
    assert "page=8" not in url
    assert url.endswith("page=3")


def test_pagination_can_normalize_items_on_page_for_static_html():
    url = build_page_url(SEARCH_URL + "&items_on_page=100", 2, items_on_page=20)
    assert "items_on_page=100" not in url
    assert "items_on_page=20" in url
    assert "search_field=name" in url
    assert "search_field=description" in url
    assert url.endswith("page=2")


def test_preview_snapshot_store_is_immutable_copy():
    vacancies, _ = parse_search_vacancies(sample_html(), SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume", vacancies)
    snap = SNAPSHOTS.get(snapshot_id)
    snap["vacancies"].append(SearchVacancy("333", "https://hh.ru/vacancy/333", "Extra", SEARCH_URL, 0))
    assert len(SNAPSHOTS.get(snapshot_id)["vacancies"]) == 2


def test_snapshot_remove_deletes_vacancy_before_start():
    client = TestClient(app)
    vacancies, _ = parse_search_vacancies(sample_html(), SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume-remove", vacancies)
    response = client.post("/api/search/snapshot/remove", json={"snapshot_id": snapshot_id, "vacancy_id": "111"})
    assert response.status_code == 200
    data = response.json()
    assert data["count"] == 1
    assert [v["id"] for v in data["vacancies"]] == ["222"]


def test_preview_reports_duplicates_and_stops_after_empty_page(monkeypatch):
    calls = []
    pages = [
        """
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">One</a></div>
        </div>
        """,
        """
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">One again</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/222">Two</a></div>
        </div>
        """,
        '<div data-qa="vacancy-serp__results"></div>',
        """
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/333">Should not load</a></div>
        </div>
        """,
    ]

    def fake_get(url, **kwargs):
        calls.append(url)
        return FakeResponse(pages[len(calls) - 1])

    monkeypatch.setattr("app.search_service.HH.get", fake_get)
    session = SessionData(cookies={"hhtoken": "h"}, headers={}, selected_resume_hash="resume-preview")
    result = preview_search(session, "https://hh.ru/search/vacancy?text=go&search_field=name", 10)
    assert result["count"] == 2
    assert result["cards_total"] == 3
    assert result["duplicate_count"] == 1
    assert result["duplicate_examples"][0]["id"] == "111"
    assert [d["page"] for d in result["diagnostics"]] == [0, 1, 2]
    assert len(calls) == 3


def test_preview_can_filter_by_title_keyword(monkeypatch):
    def fake_get(url, **kwargs):
        return FakeResponse("""
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/222">Python developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/333">Senior JAVA engineer</a></div>
        </div>
        """)

    monkeypatch.setattr("app.search_service.HH.get", fake_get)
    session = SessionData(cookies={"hhtoken": "h"}, headers={}, selected_resume_hash="resume-preview")
    result = preview_search(session, "https://kazan.hh.ru/search/vacancy?resume=abc", 1, title_keyword="java")
    assert result["count"] == 2
    assert result["filtered_by_title"] == 1
    assert result["title_keyword"] == "java"
    assert [v["id"] for v in result["vacancies"]] == ["111", "333"]


def test_preview_can_exclude_title_keywords(monkeypatch):
    def fake_get(url, **kwargs):
        return FakeResponse("""
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/222">Junior Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/333">QA automation Java</a></div>
        </div>
        """)

    monkeypatch.setattr("app.search_service.HH.get", fake_get)
    session = SessionData(cookies={"hhtoken": "h"}, headers={}, selected_resume_hash="resume-preview")
    result = preview_search(
        session,
        "https://kazan.hh.ru/search/vacancy?resume=abc",
        1,
        title_keyword="java",
        exclude_title_keywords="qa, junior",
    )
    assert result["count"] == 1
    assert result["filtered_by_title"] == 0
    assert result["excluded_by_title"] == 2
    assert result["exclude_title_keywords"] == ["qa", "junior"]
    assert [v["id"] for v in result["vacancies"]] == ["111"]


def test_preview_exclude_keywords_match_normalized_and_compact_titles(monkeypatch):
    def fake_get(url, **kwargs):
        return FakeResponse("""
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">Java backend developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/222">Full Stack Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/333">Full-stack Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/444">fullstack Java developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/555">full stack Java developer</a></div>
        </div>
        """)

    monkeypatch.setattr("app.search_service.HH.get", fake_get)
    session = SessionData(cookies={"hhtoken": "h"}, headers={}, selected_resume_hash="resume-preview")
    result = preview_search(
        session,
        "https://kazan.hh.ru/search/vacancy?resume=abc",
        1,
        title_keyword="java",
        exclude_title_keywords="fullstack",
    )
    assert result["count"] == 1
    assert result["excluded_by_title"] == 4
    assert [v["id"] for v in result["vacancies"]] == ["111"]
    assert {v["id"] for v in result["excluded_vacancies"]} == {"222", "333", "444", "555"}
    assert {v["reason"] for v in result["excluded_vacancies"]} == {"excluded_word=fullstack"}


def test_preview_exclude_keywords_support_phrases(monkeypatch):
    def fake_get(url, **kwargs):
        return FakeResponse("""
        <div data-qa="vacancy-serp__results">
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/111">Java backend developer</a></div>
          <div data-qa="vacancy-serp__vacancy"><a data-qa="serp-item__title" href="/vacancy/222">Java team lead</a></div>
        </div>
        """)

    monkeypatch.setattr("app.search_service.HH.get", fake_get)
    session = SessionData(cookies={"hhtoken": "h"}, headers={}, selected_resume_hash="resume-preview")
    result = preview_search(
        session,
        "https://kazan.hh.ru/search/vacancy?resume=abc",
        1,
        title_keyword="java",
        exclude_title_keywords="team lead",
    )
    assert result["count"] == 1
    assert result["excluded_vacancies"][0]["reason"] == "excluded_word=team lead"
    assert [v["id"] for v in result["vacancies"]] == ["111"]


def test_start_rejects_unknown_snapshot_id():
    client = TestClient(app)
    response = client.post("/api/run/start", json={"snapshot_id": "missing"})
    assert response.status_code == 400


def test_snapshot_remove_rejects_after_start():
    client = TestClient(app)
    vacancy = SearchVacancy("777", "https://hh.ru/vacancy/777", "Go", SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume-locked", [vacancy])
    worker = ApplyWorker()
    session = SessionData(cookies={"hhtoken": "h", "_xsrf": "x"}, headers={}, selected_resume_hash="resume-locked")
    worker.start(snapshot_id, RunConfig("resume-locked", "", SEARCH_URL, 1, 0, True), session)
    response = client.post("/api/search/snapshot/remove", json={"snapshot_id": snapshot_id, "vacancy_id": "777"})
    assert response.status_code == 409


def test_worker_dry_run_does_not_call_apply(monkeypatch, tmp_path):
    called = False

    def fake_apply(*args, **kwargs):
        nonlocal called
        called = True
        return "sent", {}

    monkeypatch.setattr("app.worker.send_apply", fake_apply)
    vacancy = SearchVacancy("111", "https://hh.ru/vacancy/111", "Go", SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume", [vacancy])
    worker = ApplyWorker()
    session = SessionData(cookies={"hhtoken": "h", "_xsrf": "x"}, headers={}, selected_resume_hash="resume")
    worker.start(snapshot_id, RunConfig("resume", "", SEARCH_URL, 1, 0, True), session)
    time.sleep(0.2)
    assert called is False
    assert worker.public_state()["processed"] == 1


def test_worker_skips_already_processed_vacancy(monkeypatch):
    called = False

    def fake_apply(*args, **kwargs):
        nonlocal called
        called = True
        return "sent", {}

    monkeypatch.setattr("app.worker.send_apply", fake_apply)
    monkeypatch.setattr("app.worker.is_applied", lambda resume_hash, vacancy_id: True)
    vacancy = SearchVacancy("444", "https://hh.ru/vacancy/444", "Go", SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume-processed", [vacancy])
    worker = ApplyWorker()
    session = SessionData(cookies={"hhtoken": "h", "_xsrf": "x"}, headers={}, selected_resume_hash="resume-processed")
    worker.start(snapshot_id, RunConfig("resume-processed", "", SEARCH_URL, 1, 0, False), session)
    time.sleep(0.2)
    state = worker.public_state()
    assert called is False
    assert state["processed"] == 1
    assert state["skipped"] == 1


def test_worker_counts_hh_already_separately(monkeypatch):
    marked = {}

    def fake_apply(*args, **kwargs):
        return "already", {}

    def fake_mark(resume_hash, vacancy_id, info):
        marked[vacancy_id] = info

    monkeypatch.setattr("app.worker.send_apply", fake_apply)
    monkeypatch.setattr("app.worker.is_applied", lambda resume_hash, vacancy_id: False)
    monkeypatch.setattr("app.worker.mark_applied", fake_mark)
    vacancy = SearchVacancy("555", "https://hh.ru/vacancy/555", "Go", SEARCH_URL, 0)
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume-already", [vacancy])
    worker = ApplyWorker()
    session = SessionData(cookies={"hhtoken": "h", "_xsrf": "x"}, headers={}, selected_resume_hash="resume-already")
    worker.start(snapshot_id, RunConfig("resume-already", "", SEARCH_URL, 1, 0, False), session)
    time.sleep(0.2)
    state = worker.public_state()
    assert state["processed"] == 1
    assert state["already"] == 1
    assert state["skipped"] == 0
    assert "555" in marked


def test_stop_stops_before_next_item():
    vacancies = [
        SearchVacancy(str(i), f"https://hh.ru/vacancy/{i}", f"Job {i}", SEARCH_URL, 0)
        for i in (111, 222, 333)
    ]
    snapshot_id = SNAPSHOTS.create(SEARCH_URL, "resume-stop", vacancies)
    worker = ApplyWorker()
    session = SessionData(cookies={"hhtoken": "h", "_xsrf": "x"}, headers={}, selected_resume_hash="resume-stop")
    worker.start(snapshot_id, RunConfig("resume-stop", "", SEARCH_URL, 1, 0.2, True), session)
    time.sleep(0.05)
    worker.stop()
    time.sleep(0.35)
    state = worker.public_state()
    assert state["status"] in {"stopped", "done"}
    assert state["processed"] < 3


def test_classify_apply_response_cases():
    assert classify_apply_response(200, '{"success":true}')[0] == "sent"
    assert classify_apply_response(200, '{"responseStatus":{"alreadyApplied":true}}')[0] == "already"
    assert classify_apply_response(200, '{"error":"test-required"}')[0] == "test"
    assert classify_apply_response(403, "forbidden")[0] == "auth_error"
