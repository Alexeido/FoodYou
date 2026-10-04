"""Los documentos y su mezcla. Es todo el protocolo: el servidor no entiende de
comidas, solo guarda campos JSON y, campo a campo, se queda con el más reciente.
"""

from __future__ import annotations

import json
import re
import sqlite3
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Any, Optional

KIND = re.compile(r"^[a-z_]{1,32}$")
DOC_ID = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
DELETED = "_deleted"


class InvalidChange(ValueError):
    """La petición no cumple el protocolo. No se aplica nada."""


class TooLarge(ValueError):
    pass


@dataclass(frozen=True)
class FieldValue:
    value: Any
    clock: int
    device: str

    def beats(self, other: "FieldValue") -> bool:
        """Gana el reloj más alto; a igualdad, el dispositivo mayor como texto. Así todos
        los servidores y clientes llegan al mismo resultado."""
        return (self.clock, self.device) > (other.clock, other.device)

    def to_json(self) -> dict:
        return {"value": self.value, "clock": self.clock, "device": self.device}


def now_ms() -> int:
    return int(time.time() * 1000)


def _now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_changes(raw: Any, device: str, *, max_changes: int, max_doc_bytes: int, max_skew_ms: int):
    """Valida la lista de cambios entera antes de tocar nada (todo o nada)."""
    if not isinstance(raw, list):
        raise InvalidChange("changes tiene que ser una lista")
    if len(raw) > max_changes:
        raise TooLarge(f"Como mucho {max_changes} cambios por petición")
    limit = now_ms() + max_skew_ms
    parsed = []
    for i, change in enumerate(raw):
        if not isinstance(change, dict):
            raise InvalidChange(f"changes[{i}] no es un objeto")
        kind, doc_id, fields = change.get("kind"), change.get("id"), change.get("fields")
        if not isinstance(kind, str) or not KIND.match(kind):
            raise InvalidChange(f"changes[{i}].kind no es válido")
        if not isinstance(doc_id, str) or not DOC_ID.match(doc_id):
            raise InvalidChange(f"changes[{i}].id no es válido")
        if not isinstance(fields, dict) or not fields:
            raise InvalidChange(f"changes[{i}].fields tiene que ser un objeto no vacío")
        if len(json.dumps(change)) > max_doc_bytes:
            raise TooLarge(f"changes[{i}] pasa de {max_doc_bytes} bytes")
        values: dict[str, FieldValue] = {}
        for name, field in fields.items():
            if not isinstance(name, str) or not 1 <= len(name) <= 64:
                raise InvalidChange(f"changes[{i}]: nombre de campo no válido")
            if not isinstance(field, dict) or "value" not in field:
                raise InvalidChange(f"changes[{i}].fields.{name} tiene que llevar value")
            clock = field.get("clock")
            if not isinstance(clock, int) or isinstance(clock, bool) or clock < 0:
                raise InvalidChange(f"changes[{i}].fields.{name}.clock tiene que ser un entero")
            if name == DELETED and not isinstance(field["value"], bool):
                raise InvalidChange(f"changes[{i}]: {DELETED} tiene que ser true o false")
            values[name] = FieldValue(field["value"], min(clock, limit), device)
        parsed.append((kind, doc_id, values))
    return parsed


def apply_changes(conn: sqlite3.Connection, account_id: int, changes) -> int:
    """Mezcla los cambios. Solo los documentos que de verdad cambian reciben seq nuevo.
    Devuelve cuántos cambiaron."""
    changed_docs = 0
    for kind, doc_id, incoming in changes:
        row = conn.execute(
            "SELECT fields FROM documents WHERE account_id = ? AND kind = ? AND id = ?",
            (account_id, kind, doc_id),
        ).fetchone()
        stored: dict[str, FieldValue] = {}
        if row is not None:
            stored = {
                name: FieldValue(f["value"], f["clock"], f["device"])
                for name, f in json.loads(row["fields"]).items()
            }

        changed = False
        for name, value in incoming.items():
            current = stored.get(name)
            if current is None or value.beats(current):
                stored[name] = value
                changed = True
        if not changed:
            continue
        changed_docs += 1

        seq = conn.execute(
            "UPDATE accounts SET seq = seq + 1 WHERE id = ? RETURNING seq", (account_id,)
        ).fetchone()["seq"]
        deleted = stored.get(DELETED)
        fields_json = json.dumps({n: v.to_json() for n, v in stored.items()}, ensure_ascii=False)
        conn.execute(
            """
            INSERT INTO documents (account_id, kind, id, fields, deleted, seq, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(account_id, kind, id) DO UPDATE SET
                fields = excluded.fields, deleted = excluded.deleted,
                seq = excluded.seq, updated_at = excluded.updated_at
            """,
            (
                account_id,
                kind,
                doc_id,
                fields_json,
                1 if deleted is not None and deleted.value is True else 0,
                seq,
                _now_iso(),
            ),
        )
    return changed_docs


def documents_since(
    conn: sqlite3.Connection, account_id: int, cursor: int, limit: int
) -> tuple[list[dict], int, bool]:
    """Los documentos con seq mayor que `cursor`, en orden, como mucho `limit`.

    Un cursor por delante de la cuenta (la cuenta se recreó, o el cliente venía de otro
    servidor) no puede significar nada: se le manda todo desde el principio."""
    if cursor > _account_seq(conn, account_id):
        cursor = 0
    rows = conn.execute(
        "SELECT kind, id, fields, deleted, seq FROM documents "
        "WHERE account_id = ? AND seq > ? ORDER BY seq LIMIT ?",
        (account_id, cursor, limit + 1),
    ).fetchall()
    more = len(rows) > limit
    documents = [
        {
            "kind": r["kind"],
            "id": r["id"],
            "seq": r["seq"],
            "deleted": bool(r["deleted"]),
            "fields": json.loads(r["fields"]),
        }
        for r in rows[:limit]
    ]
    new_cursor = documents[-1]["seq"] if documents else cursor
    return documents, new_cursor, more


def _account_seq(conn: sqlite3.Connection, account_id: int) -> int:
    return conn.execute("SELECT seq FROM accounts WHERE id = ?", (account_id,)).fetchone()["seq"]


def status(conn: sqlite3.Connection, account_id: int) -> dict:
    count = conn.execute(
        "SELECT COUNT(*) c FROM documents WHERE account_id = ?", (account_id,)
    ).fetchone()["c"]
    return {"cursor": _account_seq(conn, account_id), "documents": count, "serverTime": now_ms()}
