import time
from typing import Any, Dict, List, Optional
from urllib.parse import urlencode, urljoin

import requests
from bs4 import BeautifulSoup

from config import settings
from crawlers.errors import CrawlerDisabledError


def _require_crawling_enabled() -> None:
    if not settings.allow_network_crawling:
        raise CrawlerDisabledError(
            "Crawler de rede desativado. Defina JOBHUNTER_ALLOW_NETWORK_CRAWLING=true para habilitar."
        )


def buscar_indeed(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    """
    Best-effort crawler. Pode quebrar se o site mudar e pode ser bloqueado.
    Use com responsabilidade e respeite os termos/robots da plataforma.
    """
    _require_crawling_enabled()

    limit = max(1, min(int(limit), 100))
    base = "https://br.indeed.com/jobs"
    headers = {
        "User-Agent": settings.user_agent,
        "Accept-Language": "pt-BR,pt;q=0.9,en;q=0.8",
    }

    vagas: List[Dict[str, Any]] = []
    start = 0

    while len(vagas) < limit:
        params = {"q": keyword, "start": start}
        if location:
            params["l"] = location

        url = f"{base}?{urlencode(params)}"
        r = requests.get(url, headers=headers, timeout=settings.request_timeout_s)
        r.raise_for_status()

        soup = BeautifulSoup(r.text, "html.parser")
        cards = soup.select(".job_seen_beacon")
        if not cards:
            break

        for job in cards:
            if len(vagas) >= limit:
                break

            title_el = job.select_one("h2")
            title = title_el.get_text(" ", strip=True) if title_el else ""

            a = (
                job.select_one("a.jcs-JobTitle")
                or job.select_one("a[data-jk]")
                or job.select_one("a[href]")
            )
            href = a.get("href") if a else None
            if not href:
                continue
            link = urljoin("https://br.indeed.com", href)

            company_el = job.select_one("[data-testid='company-name']") or job.select_one(".companyName")
            company = company_el.get_text(" ", strip=True) if company_el else None

            loc_el = job.select_one("[data-testid='text-location']") or job.select_one(".companyLocation")
            loc = loc_el.get_text(" ", strip=True) if loc_el else None

            snippet_el = job.select_one("[data-testid='jobsnippet']") or job.select_one(".job-snippet")
            snippet = snippet_el.get_text(" ", strip=True) if snippet_el else None

            vagas.append(
                {
                    "titulo": title,
                    "empresa": company,
                    "local": loc,
                    "plataforma": "indeed",
                    "link": link,
                    "resumo": snippet,
                }
            )

        start += 10
        time.sleep(0.6)

    return vagas
