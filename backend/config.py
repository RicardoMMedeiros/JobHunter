import os
from dataclasses import dataclass


def _env_bool(name: str, default: bool = False) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    return value.strip().lower() in ("1", "true", "yes", "y", "on")


def _env_csv(name: str, default: str = "") -> list:
    raw = os.getenv(name, default)
    parts = [p.strip() for p in raw.split(",") if p.strip()]
    return parts


@dataclass(frozen=True)
class Settings:
    # OpenAI
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    openai_model: str = os.getenv("OPENAI_MODEL", "gpt-4o-mini")

    # Database
    db_path: str = os.getenv(
        "JOBHUNTER_DB_PATH",
        os.path.join(os.path.dirname(__file__), "database", "jobhunter.sqlite3"),
    )

    # Crawler behavior
    # Default is OFF to avoid accidental TOS/robots violations. Enable explicitly.
    allow_network_crawling: bool = _env_bool("JOBHUNTER_ALLOW_NETWORK_CRAWLING", False)
    request_timeout_s: float = float(os.getenv("JOBHUNTER_REQUEST_TIMEOUT_S", "20"))
    max_jobs_default: int = int(os.getenv("JOBHUNTER_MAX_JOBS_DEFAULT", "20"))
    user_agent: str = os.getenv(
        "JOBHUNTER_USER_AGENT",
        "jobhunter_ai/0.1 (research tool; set a real contact in JOBHUNTER_USER_AGENT)",
    )

    # Sources
    sources_default: str = os.getenv(
        "JOBHUNTER_SOURCES_DEFAULT",
        "indeed,nerdin,mentoradados,vagascom",
    )

    # Gupy: provide one or more company career URLs, comma-separated.
    # Example: https://werecruiter.gupy.io/,https://gupy.gupy.io/
    gupy_company_urls: tuple = tuple(_env_csv("JOBHUNTER_GUPY_COMPANY_URLS", ""))

    # Auto-apply safety
    # This project intentionally does not auto-submit applications. It only previews steps.
    allow_auto_submit: bool = _env_bool("JOBHUNTER_ALLOW_AUTO_SUBMIT", False)


settings = Settings()

# Back-compat with the user's original snippet
OPENAI_API_KEY = settings.openai_api_key
