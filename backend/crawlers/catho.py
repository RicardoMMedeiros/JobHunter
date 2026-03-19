from typing import Any, Dict, List, Optional

from crawlers.errors import CrawlerDisabledError


def buscar_catho(keyword: str, location: Optional[str] = None, limit: int = 20) -> List[Dict[str, Any]]:
    # Catho pode exigir login/assinatura e possui protecoes.
    raise CrawlerDisabledError(
        "Catho crawler desativado por padrao (login/termos)."
    )
