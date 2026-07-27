from __future__ import annotations

import os
import uuid

from fastapi import FastAPI

from app.routes import COVER_LETTERS, internal_router, router, safe_error_handler


app = FastAPI(title="HH Adapter")
app.include_router(router)
app.include_router(internal_router)
app.add_exception_handler(Exception, safe_error_handler)


@app.middleware("http")
async def request_id_middleware(request, call_next):
    request_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
    response = await call_next(request)
    response.headers["X-Request-Id"] = request_id
    return response


@app.on_event("shutdown")
async def shutdown_llm_client() -> None:
    await COVER_LETTERS.aclose()


def host() -> str:
    return os.environ.get("HH_CLEAN_HOST", "127.0.0.1")
