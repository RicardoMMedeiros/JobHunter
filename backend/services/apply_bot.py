from __future__ import annotations

from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import List

from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright

from config import settings


@dataclass(frozen=True)
class ApplyPreview:
    url: str
    detected_ctas: List[str]
    notes: List[str]
    captured_at: str


def preview_apply_flow(url: str, timeout_ms: int = 15000) -> ApplyPreview:
    """
    MODO SEGURO: este metodo NAO clica em "Candidatar"/"Apply" e NAO envia formularios.
    Ele apenas abre a pagina, tenta identificar CTAs e retorna um resumo para o usuario.
    """
    notes: List[str] = []
    detected: List[str] = []

    if settings.allow_auto_submit:
        notes.append(
            "JOBHUNTER_ALLOW_AUTO_SUBMIT=true esta ativado, mas este projeto nao implementa auto-submit."
        )

    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        page = browser.new_page()
        try:
            page.goto(url, wait_until="domcontentloaded", timeout=timeout_ms)
            page.wait_for_timeout(800)

            candidates = ["Candidatar", "Candidatura", "Aplicar", "Apply", "Quick apply", "Easy Apply"]
            for label in candidates:
                locator = page.get_by_text(label, exact=False)
                try:
                    if locator.first.is_visible(timeout=800):
                        detected.append(label)
                except PlaywrightTimeoutError:
                    pass

            if not detected:
                notes.append("Nenhum CTA obvio encontrado no texto. Pode estar em iframe, modal ou exigir login.")
            notes.append("Para candidatar, abra o link no navegador com sua conta e finalize manualmente.")
        finally:
            browser.close()

    return ApplyPreview(
        url=url,
        detected_ctas=sorted(set(detected)),
        notes=notes,
        captured_at=datetime.now(timezone.utc).isoformat(),
    )


def preview_apply_flow_dict(url: str, timeout_ms: int = 15000) -> dict:
    return asdict(preview_apply_flow(url=url, timeout_ms=timeout_ms))
