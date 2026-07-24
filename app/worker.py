from __future__ import annotations

import threading
import time

from app.apply_service import send_apply
from app.models import RunConfig, SearchVacancy, SessionData, WorkerState
from app.search_service import SNAPSHOTS
from app.storage import is_applied, mark_applied


class ApplyWorker:
    def __init__(self):
        self._lock = threading.RLock()
        self._thread: threading.Thread | None = None
        self.state = WorkerState()
        self._active_snapshot_id = ""

    def public_state(self) -> dict:
        with self._lock:
            return self.state.to_dict()

    def stop(self) -> None:
        with self._lock:
            self.state.stop_requested = True
            if self.state.status == "running":
                self.state.status = "stopping"
                self.state.log("STOP requested")

    def start(self, snapshot_id: str, config: RunConfig, session: SessionData) -> None:
        snap = SNAPSHOTS.get(snapshot_id)
        if not snap:
            raise ValueError("unknown snapshot_id")
        if snap["resume_hash"] != config.resume_hash:
            raise ValueError("selected resume differs from snapshot")
        with self._lock:
            if self._thread and self._thread.is_alive():
                raise RuntimeError("worker already running")
            SNAPSHOTS.lock(snapshot_id)
            self.state = WorkerState(
                status="running",
                collected=len(snap["vacancies"]),
                snapshot_id=snapshot_id,
            )
            self._active_snapshot_id = snapshot_id
            vacancies = list(snap["vacancies"])
            self._thread = threading.Thread(
                target=self._run,
                args=(snapshot_id, vacancies, config, session),
                daemon=True,
            )
            self._thread.start()

    def _valid_member(self, vacancy: SearchVacancy, snapshot_id: str, snapshot_set: set[SearchVacancy], config: RunConfig) -> bool:
        if snapshot_id != self._active_snapshot_id:
            return False
        if vacancy not in snapshot_set:
            return False
        if not vacancy.id.isdigit():
            return False
        if vacancy.url != f"https://hh.ru/vacancy/{vacancy.id}":
            return False
        if vacancy.source_search_url != config.search_url:
            return False
        if vacancy.source_page < 0 or vacancy.source_page >= config.pages:
            return False
        return True

    def _run(self, snapshot_id: str, vacancies: list[SearchVacancy], config: RunConfig, session: SessionData) -> None:
        snapshot_set = set(vacancies)
        try:
            for vacancy in vacancies:
                with self._lock:
                    if self.state.stop_requested:
                        self.state.status = "stopped"
                        self.state.log("STOPPED before next request")
                        return
                    self.state.current_vacancy = f"{vacancy.id} {vacancy.title[:80]}"

                if not self._valid_member(vacancy, snapshot_id, snapshot_set, config):
                    with self._lock:
                        self.state.skipped += 1
                        self.state.processed += 1
                        self.state.log(f"SKIP vacancy={vacancy.id} reason=not_in_search_snapshot")
                    continue

                if session.selected_resume_hash != config.resume_hash:
                    with self._lock:
                        self.state.skipped += 1
                        self.state.processed += 1
                        self.state.log(f"SKIP vacancy={vacancy.id} reason=resume_changed")
                    continue

                if is_applied(config.resume_hash, vacancy.id):
                    with self._lock:
                        self.state.skipped += 1
                        self.state.processed += 1
                        self.state.log(f"SKIP vacancy={vacancy.id} reason=already_processed")
                    continue

                if config.dry_run:
                    with self._lock:
                        self.state.processed += 1
                        self.state.skipped += 1
                        self.state.log(f"DRY_RUN vacancy={vacancy.id} title={vacancy.title[:120]}")
                else:
                    result, info = send_apply(session, vacancy.id, config.cover_letter)
                    with self._lock:
                        self.state.processed += 1
                        if result == "sent":
                            self.state.applied += 1
                            mark_applied(config.resume_hash, vacancy.id, {"title": vacancy.title, "url": vacancy.url, **info})
                            self.state.log(f"APPLIED vacancy={vacancy.id}")
                        elif result == "already":
                            self.state.already += 1
                            mark_applied(config.resume_hash, vacancy.id, {"title": vacancy.title, "url": vacancy.url, "hh_status": "already", **info})
                            self.state.log(f"ALREADY vacancy={vacancy.id}")
                        elif result in {"test", "limit", "auth_error"}:
                            self.state.skipped += 1
                            self.state.log(f"SKIP vacancy={vacancy.id} reason={result}")
                            if result in {"limit", "auth_error"}:
                                self.state.status = "stopped"
                                return
                        else:
                            self.state.failed += 1
                            self.state.log(f"ERROR vacancy={vacancy.id} reason=apply_error")

                if config.delay_seconds > 0:
                    time.sleep(config.delay_seconds)

            with self._lock:
                if self.state.status != "stopped":
                    self.state.status = "done"
                    self.state.current_vacancy = ""
                    self.state.log("DONE")
        except Exception as exc:
            with self._lock:
                self.state.status = "error"
                self.state.failed += 1
                self.state.log(f"ERROR worker={type(exc).__name__}: {str(exc)[:120]}")


WORKER = ApplyWorker()
