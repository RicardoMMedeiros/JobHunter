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


def buscar_mentoradados(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    _require_enabled()

    url = "https://mentoradados.com/portal-de-vagas/"
    headers = {"User-Agent": settings.user_agent, "Accept-Language": "pt-BR,pt;q=0.9"}

    r = requests.get(url, headers=headers, timeout=settings.request_timeout_s)
    r.raise_for_status()

    soup = BeautifulSoup(r.text, "html.parser")
    kw = (keyword or "").strip().lower()

    vagas: List[Dict[str, Any]] = []

    # Heuristic: many entries include a "Candidate-se" link.
    for a in soup.select("a[href]"):
        text = a.get_text(" ", strip=True).lower()
        if "candidate" not in text and "candid" not in text:
            continue

        href = a.get("href") or ""
        if not href:
            continue

        link = urljoin(url, href)

        # Try to find a nearby title
        title = "Vaga (Mentora Dados)"
        parent = a
        for _ in range(6):
            parent = parent.parent
            if not parent:
                break
            h = parent.find(["h1", "h2", "h3", "h4", "strong"])
            if h:
                cand = h.get_text(" ", strip=True)
                if cand and len(cand) >= 4:
                    title = cand
                    break

        if kw and kw not in title.lower():
            continue

        vagas.append(
            {
                "titulo": title,
                "empresa": "Mentora Dados",
                "local": location,
                "plataforma": "mentoradados",
                "link": link,
                "resumo": None,
            }
        )
        if len(vagas) >= limit:
            break

    uniq = {}
    for v in vagas:
        uniq[v["link"]] = v

    return list(uniq.values())[: max(1, min(int(limit), 100))]
