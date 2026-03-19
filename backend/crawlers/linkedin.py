from typing import Any, Dict, List, Optional

from crawlers.errors import CrawlerDisabledError


def buscar_linkedin(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    # LinkedIn geralmente exige login e possui termos/controles anti-bot.
    # Este projeto nao implementa scraping/login automatizado.
    raise CrawlerDisabledError(
        "LinkedIn crawler desativado por padrao (login/termos). Use export/manual ou APIs oficiais/partners."
    )
