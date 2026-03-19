import os
from typing import Any, Dict, List, Optional

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, RedirectResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel, Field

from config import settings
from crawlers.errors import CrawlerDisabledError
from database.db import get_job_detail, init_db, list_jobs, list_matches, store_job_detail
from services.ai_match import score as score_job
from services.apply_bot import preview_apply_flow_dict
from services.collector import available_sources, coletar, coletar_debug


app = FastAPI(title="jobhunter_ai", version="0.2.0")


@app.on_event("startup")
def _startup() -> None:
    init_db()


_UI_DIR = os.path.join(os.path.dirname(__file__), "ui")
_UI_STATIC = os.path.join(_UI_DIR, "static")
_UI_INDEX = os.path.join(_UI_DIR, "index.html")

if os.path.isdir(_UI_STATIC):
    app.mount("/ui/static", StaticFiles(directory=_UI_STATIC), name="ui-static")


@app.get("/")
def root() -> RedirectResponse:
    return RedirectResponse(url="/ui")


@app.get("/ui")
def ui() -> FileResponse:
    if not os.path.isfile(_UI_INDEX):
        raise HTTPException(status_code=404, detail="UI not found")
    return FileResponse(_UI_INDEX)


@app.get("/config")
def config() -> Dict[str, Any]:
    return {
        "allow_network_crawling": settings.allow_network_crawling,
        "sources_default": settings.sources_default,
        "available_sources": available_sources(),
        "gupy_company_urls": list(settings.gupy_company_urls),
        "openai_model": settings.openai_model,
        "auto_submit_supported": False,
    }


@app.get("/health")
def health() -> Dict[str, Any]:
    return {"ok": True}


@app.get("/buscar")
def buscar(
    keyword: str,
    location: Optional[str] = None,
    limit: int = 20,
    sources: Optional[str] = None,
) -> List[Dict[str, Any]]:
    try:
        return coletar(keyword=keyword, location=location, limit=limit, persist=True, sources=sources)
    except CrawlerDisabledError as e:
        raise HTTPException(status_code=400, detail=str(e))


@app.get("/buscar_debug")
def buscar_debug(
    keyword: str,
    location: Optional[str] = None,
    limit: int = 20,
    sources: Optional[str] = None,
) -> Dict[str, Any]:
    jobs, errors = coletar_debug(
        keyword=keyword,
        location=location,
        limit=limit,
        persist=True,
        sources=sources,
    )
    return {"jobs": jobs, "errors": errors}


@app.get("/jobs")
def jobs(limit: int = 50) -> List[Dict[str, Any]]:
    return list_jobs(limit=limit)


@app.get("/matches")
def matches(limit: int = 50) -> List[Dict[str, Any]]:
    return list_matches(limit=limit)


class MatchRequest(BaseModel):
    curriculo: str = Field(..., description="Texto do curriculo")
    descricao: str = Field(..., description="Texto/descricao da vaga")
    job_url: Optional[str] = Field(None, description="URL da vaga (opcional, para persistir match)")


@app.post("/match")
def match(req: MatchRequest) -> Dict[str, Any]:
    try:
        value = score_job(curriculo=req.curriculo, descricao=req.descricao, job_url=req.job_url)
        return {"score": value}
    except Exception as e:
        raise HTTPException(status_code=400, detail=str(e))


class DetailRequest(BaseModel):
    url: str
    description: str


@app.post("/jobs/detail")
def save_detail(req: DetailRequest) -> Dict[str, Any]:
    store_job_detail(url=req.url, description=req.description)
    return {"ok": True}


@app.get("/jobs/detail")
def read_detail(url: str) -> Dict[str, Any]:
    d = get_job_detail(url=url)
    if not d:
        raise HTTPException(status_code=404, detail="Nao encontrado")
    return d


class ApplyPreviewRequest(BaseModel):
    url: str
    timeout_ms: int = 15000


@app.post("/apply/preview")
def apply_preview(req: ApplyPreviewRequest) -> Dict[str, Any]:
    return preview_apply_flow_dict(url=req.url, timeout_ms=req.timeout_ms)
