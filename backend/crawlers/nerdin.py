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


def buscar_nerdin(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    _require_enabled()

    base_url = "https://nerdin.com.br/vagas"
    headers = {"User-Agent": settings.user_agent, "Accept-Language": "pt-BR,pt;q=0.9"}

    r = requests.get(base_url, headers=headers, timeout=settings.request_timeout_s)
    r.raise_for_status()

    soup = BeautifulSoup(r.text, "html.parser")

    vagas: List[Dict[str, Any]] = []
    kw = (keyword or "").strip().lower()

    for a in soup.select("a[href]"):
        href = a.get("href") or ""
        if "/vaga/" not in href:
            continue

        link = urljoin("https://nerdin.com.br", href)

        title = a.get_text(" ", strip=True)
        if not title or title.lower() in ("ver vaga", "quero essa vaga", "saiba mais"):
            # Try to find a nearby heading
            parent = a
            for _ in range(4):
                parent = parent.parent
                if not parent:
                    break
                h = parent.find(["h1", "h2", "h3", "h4"])
                if h:
                    title = h.get_text(" ", strip=True)
                    break

        if not title:
            continue

        if kw and kw not in title.lower():
            continue

        vagas.append(
            {
                "titulo": title,
                "empresa": None,
                "local": location,
                "plataforma": "nerdin",
                "link": link,
                "resumo": None,
            }
        )
        if len(vagas) >= limit:
            break

    # Some pages may require filtering from all text; keep best-effort.
    # De-dup by link
    uniq = {}
    for v in vagas:
        uniq[v["link"]] = v

    return list(uniq.values())[: max(1, min(int(limit), 100))]
