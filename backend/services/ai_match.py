import hashlib
import re
from typing import Optional

from openai import OpenAI

from config import settings
from database.db import store_match


def _extract_score(text: str) -> int:
    m = re.search(r"(\d{1,3})", text or "")
    if not m:
        return 0
    value = int(m.group(1))
    return max(0, min(value, 100))


def _hash_resume(resume_text: str) -> str:
    return hashlib.sha256(resume_text.encode("utf-8")).hexdigest()


def score(curriculo: str, descricao: str, job_url: Optional[str] = None) -> int:
    if not settings.openai_api_key:
        raise RuntimeError("OPENAI_API_KEY nao configurada. Defina a variavel de ambiente OPENAI_API_KEY.")

    client = OpenAI(api_key=settings.openai_api_key)

    prompt = (
        "Avalie compatibilidade entre curriculo e vaga.\n"
        "Responda APENAS com um numero inteiro de 0 a 100.\n\n"
        f"CURRICULO:\n{curriculo}\n\n"
        f"VAGA:\n{descricao}\n"
    )

    r = client.chat.completions.create(
        model=settings.openai_model,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.2,
    )

    content = (r.choices[0].message.content or "").strip()
    value = _extract_score(content)

    if job_url:
        store_match(job_url=job_url, resume_hash=_hash_resume(curriculo), score=value, model=settings.openai_model)

    return value
