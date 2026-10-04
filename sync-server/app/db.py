from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path

from app.config import get_settings

SCHEMA = Path(__file__).with_name("schema.sql")


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(get_settings().db_full_path, timeout=30, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA foreign_keys = ON")
    return conn


# Columnas añadidas a tablas que ya existían: CREATE TABLE IF NOT EXISTS no toca una
# tabla ya creada, así que una base de datos de antes necesita que se le añadan aquí.
_ADDED_COLUMNS = {
    "accounts": {
        "is_admin": "INTEGER NOT NULL DEFAULT 0",
        "mcp_enabled": "INTEGER NOT NULL DEFAULT 0",
        "password_is_default": "INTEGER NOT NULL DEFAULT 0",
        "note": "TEXT",
        # La cuenta del servidor de alimentos (foods) en cuyo nombre busca el MCP.
        "food_account": "TEXT",
    },
    # Los tokens que da OAuth caducan y se renuevan con su token de refresco; los demás no.
    "tokens": {
        "expires_at": "TEXT",
        "refresh_hash": "TEXT",
        "client_id": "TEXT",
    },
}


def init_schema() -> None:
    conn = connect()
    try:
        conn.executescript(SCHEMA.read_text(encoding="utf-8"))
        for table, columns in _ADDED_COLUMNS.items():
            existing = {r["name"] for r in conn.execute(f"PRAGMA table_info({table})")}
            for name, ddl in columns.items():
                if name not in existing:
                    conn.execute(f"ALTER TABLE {table} ADD COLUMN {name} {ddl}")
    finally:
        conn.close()


@contextmanager
def transaction():
    """BEGIN IMMEDIATE: una sincronización se aplica entera o no se aplica, y dos a la
    vez de la misma cuenta no se pisan los seq."""
    conn = connect()
    try:
        conn.execute("BEGIN IMMEDIATE")
        try:
            yield conn
        except BaseException:
            conn.execute("ROLLBACK")
            raise
        conn.execute("COMMIT")
    finally:
        conn.close()
