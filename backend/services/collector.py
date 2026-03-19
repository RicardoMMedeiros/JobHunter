from typing import Any, Dict, List, Optional, Tuple

from config import settings
from crawlers.catho import buscar_catho
from crawlers.gupy import buscar_gupy
from crawlers.indeed import buscar_indeed
from crawlers.linkedin import buscar_linkedin
from crawlers.mentoradados import buscar_mentoradados
from crawlers.nerdin import buscar_nerdin
from crawlers.vagascom import buscar_vagascom
from database.db import upsert_job


_CRAWLERS = {
    "indeed": buscar_indeed,
    "linkedin": buscar_linkedin,
    "nerdin": buscar_nerdin,
    "mentoradados": buscar_mentoradados,
    "vagascom": buscar_vagascom,
    "catho": buscar_catho,
    "gupy": buscar_gupy,
}


def available_sources() -> List[str]:
    return sorted(_CRAWLERS.keys())


def _parse_sources(sources: Optional[str]) -> List[str]:
    raw = settings.sources_default if sources is None else sources
    items = [s.strip().lower() for s in (raw or "").split(",") if s.strip()]
    return items or ["indeed"]


def _dedupe_and_limit(vagas: List[Dict[str, Any]], limit: int) -> List[Dict[str, Any]]:
    uniq: Dict[str, Dict[str, Any]] = {}
    for v in vagas:
        link = v.get("link") or v.get("url")
        if not link:
            continue
        uniq[link] = v
    safe_limit = max(1, min(int(limit), 200))
    return list(uniq.values())[:safe_limit]


def coletar_debug(
    keyword: str,
    location: Optional[str] = None,
    limit: int = 20,
    persist: bool = True,
    sources: Optional[str] = None,
) -> Tuple[List[Dict[str, Any]], List[Dict[str, str]]]:
    selected = _parse_sources(sources)

    vagas: List[Dict[str, Any]] = []
    errors: List[Dict[str, str]] = []

    for src in selected:
        fn = _CRAWLERS.get(src)
        if not fn:
            errors.append({"source": src, "error": "source_not_found"})
            continue

        try:
            vagas += fn(keyword=keyword, location=location, limit=limit)
        except Exception as e:
            errors.append({"source": src, "error": str(e)})

    out = _dedupe_and_limit(vagas, limit=limit)

    if persist:
        for v in out:
            upsert_job(v)

    return out, errors


def coletar(
    keyword: str,
    location: Optional[str] = None,
    limit: int = 20,
    persist: bool = True,
    sources: Optional[str] = None,
) -> List[Dict[str, Any]]:
    out, _ = coletar_debug(
        keyword=keyword,
        location=location,
        limit=limit,
        persist=persist,
        sources=sources,
    )
    return out
