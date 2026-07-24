from __future__ import annotations

import os

from fastapi import FastAPI

from app.routes import router


app = FastAPI(title="HH Clicker Clean")
app.include_router(router)


def host() -> str:
    return os.environ.get("HH_CLEAN_HOST", "127.0.0.1")
