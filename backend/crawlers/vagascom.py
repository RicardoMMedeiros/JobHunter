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


def _slugify(text: str) -> str:
    t = (text or "").strip().lower()
    t = re.sub(r"\s+", "-", t)
    t = re.sub(r"[^a-z0-9\-]", "", t)
    t = re.sub(r"-+", "-", t)
    return t.strip("-")


def buscar_vagascom(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    _require_enabled()

    slug = _slugify(keyword)
    if not slug:
        return []

    url = f"https://www.vagas.com.br/vagas-de-{slug}"
    headers = {"User-Agent": settings.user_agent, "Accept-Language": "pt-BR,pt;q=0.9"}

    r = requests.get(url, headers=headers, timeout=settings.request_timeout_s)
    r.raise_for_status()

    soup = BeautifulSoup(r.text, "html.parser")
    vagas: List[Dict[str, Any]] = []

    # Heuristic: job links often contain /v and digits.
    for a in soup.select("a[href]"):
        href = a.get("href") or ""
        if not re.search(r"^/v\d+", href):
            continue

        title = a.get_text(" ", strip=True)
        if not title or len(title) < 3:
            continue

        link = urljoin("https://www.vagas.com.br", href)
        vagas.append(
            {
                "titulo": title,
                "empresa": None,
                "local": location,
                "plataforma": "vagascom",
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
