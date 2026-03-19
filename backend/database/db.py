import sqlite3
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Optional

from config import settings


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def init_db() -> None:
    with connect() as conn:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS jobs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                platform TEXT NOT NULL,
                title TEXT NOT NULL,
                company TEXT,
                location TEXT,
                url TEXT NOT NULL UNIQUE,
                snippet TEXT,
                collected_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS job_details (
                url TEXT PRIMARY KEY,
                description TEXT NOT NULL,
                fetched_at TEXT NOT NULL
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS matches (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_url TEXT NOT NULL,
                resume_hash TEXT NOT NULL,
                score INTEGER NOT NULL,
                model TEXT NOT NULL,
                created_at TEXT NOT NULL,
                UNIQUE(job_url, resume_hash, model)
            )
            """
        )
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS applications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                job_url TEXT NOT NULL,
                status TEXT NOT NULL,
                notes TEXT,
                created_at TEXT NOT NULL
            )
            """
        )
        conn.commit()


@contextmanager
def connect() -> Iterable[sqlite3.Connection]:
    conn = sqlite3.connect(settings.db_path)
    try:
        conn.row_factory = sqlite3.Row
        yield conn
    finally:
        conn.close()


def upsert_job(job: Dict[str, Any]) -> None:
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO jobs (platform, title, company, location, url, snippet, collected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(url) DO UPDATE SET
                platform=excluded.platform,
                title=excluded.title,
                company=excluded.company,
                location=excluded.location,
                snippet=excluded.snippet
            """,
            (
                job.get("plataforma") or job.get("platform") or "unknown",
                job.get("titulo") or job.get("title") or "",
                job.get("empresa") or job.get("company"),
                job.get("local") or job.get("location"),
                job.get("link") or job.get("url") or "",
                job.get("resumo") or job.get("snippet"),
                _utc_now_iso(),
            ),
        )
        conn.commit()


def list_jobs(limit: int = 50) -> List[Dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT id, platform, title, company, location, url, snippet, collected_at
            FROM jobs
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
    return [dict(r) for r in rows]


def get_job_detail(url: str) -> Optional[Dict[str, Any]]:
    with connect() as conn:
        row = conn.execute(
            "SELECT url, description, fetched_at FROM job_details WHERE url = ?",
            (url,),
        ).fetchone()
    return dict(row) if row else None


def store_job_detail(url: str, description: str) -> None:
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO job_details (url, description, fetched_at)
            VALUES (?, ?, ?)
            ON CONFLICT(url) DO UPDATE SET
                description=excluded.description,
                fetched_at=excluded.fetched_at
            """,
            (url, description, _utc_now_iso()),
        )
        conn.commit()


def store_match(job_url: str, resume_hash: str, score: int, model: str) -> None:
    with connect() as conn:
        conn.execute(
            """
            INSERT OR REPLACE INTO matches (job_url, resume_hash, score, model, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (job_url, resume_hash, score, model, _utc_now_iso()),
        )
        conn.commit()


def list_matches(limit: int = 50) -> List[Dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT id, job_url, resume_hash, score, model, created_at
            FROM matches
            ORDER BY id DESC
            LIMIT ?
            """,
            (limit,),
        ).fetchall()
    return [dict(r) for r in rows]
