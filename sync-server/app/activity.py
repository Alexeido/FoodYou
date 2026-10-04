"""Quién sincroniza, desde qué dispositivos y cuánto: lo que enseña el panel."""

from __future__ import annotations

import sqlite3
from datetime import datetime, timedelta, timezone
from typing import Optional

from app.accounts import now_iso


def _ago(**delta) -> str:
    return (datetime.now(timezone.utc) - timedelta(**delta)).strftime("%Y-%m-%dT%H:%M:%SZ")


def record_sync(
    conn: sqlite3.Connection,
    account_id: int,
    device: str,
    user_agent: Optional[str],
    sent: int,
    received: int,
) -> None:
    now = now_iso()
    conn.execute(
        """
        INSERT INTO devices (account_id, device, user_agent, first_seen, last_seen, syncs)
        VALUES (?, ?, ?, ?, ?, 1)
        ON CONFLICT(account_id, device) DO UPDATE SET
            last_seen = excluded.last_seen, user_agent = excluded.user_agent,
            syncs = devices.syncs + 1
        """,
        (account_id, device, (user_agent or "")[:200] or None, now, now),
    )
    # Las vueltas sin nada que contar (la app pregunta cada poco) solo actualizan el
    # dispositivo; el registro guarda las que movieron algo.
    if sent or received:
        conn.execute(
            "INSERT INTO sync_log (account_id, at, device, sent, received) VALUES (?, ?, ?, ?, ?)",
            (account_id, now, device, sent, received),
        )


def purge(conn: sqlite3.Connection, days: int = 90) -> None:
    conn.execute("DELETE FROM sync_log WHERE at < ?", (_ago(days=days),))


def overview(conn: sqlite3.Connection) -> list[dict]:
    rows = conn.execute(
        """
        SELECT a.*,
          (SELECT COUNT(*) FROM documents d WHERE d.account_id = a.id AND d.deleted = 0) docs,
          (SELECT COUNT(*) FROM devices v WHERE v.account_id = a.id) devices,
          (SELECT MAX(last_seen) FROM devices v WHERE v.account_id = a.id) last_seen,
          (SELECT COUNT(*) FROM sync_log l WHERE l.account_id = a.id AND l.at >= ?) syncs_24h,
          (SELECT COUNT(*) FROM tokens t WHERE t.account_id = a.id AND t.kind = 'mcp'
             AND t.revoked = 0) mcp_tokens
        FROM accounts a ORDER BY a.is_admin DESC, a.username
        """,
        (_ago(hours=24),),
    ).fetchall()
    return [dict(r) for r in rows]


def totals(conn: sqlite3.Connection) -> dict:
    return {
        "accounts": conn.execute("SELECT COUNT(*) c FROM accounts").fetchone()["c"],
        "syncing": conn.execute(
            "SELECT COUNT(DISTINCT account_id) c FROM devices WHERE last_seen >= ?", (_ago(days=7),)
        ).fetchone()["c"],
        "syncs_24h": conn.execute(
            "SELECT COUNT(*) c FROM sync_log WHERE at >= ?", (_ago(hours=24),)
        ).fetchone()["c"],
        "documents": conn.execute(
            "SELECT COUNT(*) c FROM documents WHERE deleted = 0"
        ).fetchone()["c"],
        "mcp_accounts": conn.execute(
            "SELECT COUNT(*) c FROM accounts WHERE mcp_enabled = 1"
        ).fetchone()["c"],
    }


def daily_syncs(conn: sqlite3.Connection, account_id: Optional[int] = None, days: int = 30) -> list[tuple[str, int]]:
    where, args = ("AND account_id = ?", [account_id]) if account_id else ("", [])
    rows = conn.execute(
        f"SELECT substr(at, 1, 10) day, COUNT(*) c FROM sync_log WHERE at >= ? {where} GROUP BY day",
        [_ago(days=days), *args],
    ).fetchall()
    by_day = {r["day"]: r["c"] for r in rows}
    today = datetime.now(timezone.utc).date()
    return [
        (d.isoformat(), by_day.get(d.isoformat(), 0))
        for d in (today - timedelta(days=i) for i in range(days - 1, -1, -1))
    ]


def devices(conn: sqlite3.Connection, account_id: int) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT * FROM devices WHERE account_id = ? ORDER BY last_seen DESC", (account_id,)
    ).fetchall()


def recent(conn: sqlite3.Connection, account_id: int, limit: int = 50) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT * FROM sync_log WHERE account_id = ? ORDER BY id DESC LIMIT ?", (account_id, limit)
    ).fetchall()


def documents_by_kind(conn: sqlite3.Connection, account_id: int) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT kind, SUM(deleted = 0) live, SUM(deleted = 1) deleted FROM documents "
        "WHERE account_id = ? GROUP BY kind ORDER BY kind",
        (account_id,),
    ).fetchall()
