"""Lo que ha hecho el MCP, para poder deshacerlo y rehacerlo.

Cada cambio guarda cómo estaban los campos que tocó (o que el documento no existía) y cómo los
dejó. Deshacer es volver a escribir los primeros y rehacer, los segundos, con un reloj nuevo,
como cualquier otro cambio: llega a todos los dispositivos igual. Vive en el SQLite propio del
MCP, por cuenta.
"""

from __future__ import annotations

import json
import sqlite3
import time
from pathlib import Path
from typing import Optional


class Journal:
    def __init__(self, path: Path) -> None:
        path.parent.mkdir(parents=True, exist_ok=True)
        self._path = path
        with self._connect() as conn:
            conn.execute(
                "CREATE TABLE IF NOT EXISTS journal (id INTEGER PRIMARY KEY AUTOINCREMENT, "
                "account TEXT NOT NULL, at INTEGER NOT NULL, summary TEXT NOT NULL, "
                "undo TEXT NOT NULL, undone INTEGER NOT NULL DEFAULT 0)"
            )
            existing = {r["name"] for r in conn.execute("PRAGMA table_info(journal)")}
            # Añadidas después: una base de datos de antes no las tiene.
            if "redo" not in existing:
                conn.execute("ALTER TABLE journal ADD COLUMN redo TEXT")
            if "undone_at" not in existing:
                conn.execute("ALTER TABLE journal ADD COLUMN undone_at INTEGER")

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self._path)
        conn.row_factory = sqlite3.Row
        return conn

    def record(self, account: str, summary: str, undo: list[dict], redo: Optional[list[dict]] = None) -> int:
        """`undo`/`redo`: [{"kind", "id", "values": {campo: valor}}] para volver atrás o adelante."""
        with self._connect() as conn:
            cur = conn.execute(
                "INSERT INTO journal (account, at, summary, undo, redo) VALUES (?, ?, ?, ?, ?)",
                (account, int(time.time()), summary, json.dumps(undo), json.dumps(redo) if redo else None),
            )
            return int(cur.lastrowid)

    def last(self, account: str) -> Optional[sqlite3.Row]:
        with self._connect() as conn:
            return conn.execute(
                "SELECT * FROM journal WHERE account = ? AND undone = 0 ORDER BY id DESC LIMIT 1",
                (account,),
            ).fetchone()

    def last_undone(self, account: str) -> Optional[sqlite3.Row]:
        """Lo último que se deshizo y se puede rehacer."""
        with self._connect() as conn:
            return conn.execute(
                "SELECT * FROM journal WHERE account = ? AND undone = 1 AND redo IS NOT NULL "
                "ORDER BY undone_at DESC, id DESC LIMIT 1",
                (account,),
            ).fetchone()

    def mark_undone(self, entry_id: int) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE journal SET undone = 1, undone_at = ? WHERE id = ?", (time.time_ns(), entry_id))

    def mark_redone(self, entry_id: int) -> None:
        with self._connect() as conn:
            conn.execute("UPDATE journal SET undone = 0, undone_at = NULL WHERE id = ?", (entry_id,))

    def recent(self, account: str, limit: int = 10) -> list[sqlite3.Row]:
        with self._connect() as conn:
            return conn.execute(
                "SELECT * FROM journal WHERE account = ? ORDER BY id DESC LIMIT ?", (account, limit)
            ).fetchall()
