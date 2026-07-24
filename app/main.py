from __future__ import annotations

import os

from fastapi import FastAPI

from app.routes import COVER_LETTERS, router


app = FastAPI(title="HH Clicker Clean")
app.include_router(router)


@app.on_event("shutdown")
async def shutdown_llm_client() -> None:
    await COVER_LETTERS.aclose()


def host() -> str:
    return os.environ.get("HH_CLEAN_HOST", "127.0.0.1")
