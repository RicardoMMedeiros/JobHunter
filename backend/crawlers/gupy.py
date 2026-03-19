import re
from typing import Any, Dict, List, Optional
from urllib.parse import urljoin

import requests
from bs4 import BeautifulSoup

from config import settings
from crawlers.errors import CrawlerDisabledError


def _require_enabled() -> None:
    if not settings.allow_network_crawling:
        raise CrawlerDisabledError(
            "Crawler de rede desativado. Defina JOBHUNTER_ALLOW_NETWORK_CRAWLING=true para habilitar."
        )


def buscar_gupy(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    """
    Gupy nao possui um buscador global simples (varia por empresa).
    Configure JOBHUNTER_GUPY_COMPANY_URLS com URLs de paginas de carreira.
    Ex: https://suaempresa.gupy.io/
    """
    _require_enabled()

    companies = list(settings.gupy_company_urls)
    if not companies:
        return []

    headers = {"User-Agent": settings.user_agent, "Accept-Language": "pt-BR,pt;q=0.9"}
    kw = (keyword or "").strip().lower()

    vagas: List[Dict[str, Any]] = []

    for base in companies:
        try:
            r = requests.get(base, headers=headers, timeout=settings.request_timeout_s)
            r.raise_for_status()
        except Exception:
            continue

        soup = BeautifulSoup(r.text, "html.parser")

        for a in soup.select("a[href]"):
            href = a.get("href") or ""
            if not href:
                continue

            # Common patterns include /jobs/ or /job/
            if "/jobs" not in href and "/job" not in href:
                continue

            title = a.get_text(" ", strip=True)
            if not title or len(title) < 3:
                continue

            if kw and kw not in title.lower():
                continue

            link = urljoin(base, href)
            vagas.append(
                {
                    "titulo": title,
                    "empresa": None,
                    "local": location,
                    "plataforma": "gupy",
                    "link": link,
                    "resumo": None,
                }
            )
            if len(vagas) >= limit:
                break

        if len(vagas) >= limit:
            break

    uniq = {}
    for v in vagas:
        uniq[v["link"]] = v

    return list(uniq.values())[: max(1, min(int(limit), 100))]
