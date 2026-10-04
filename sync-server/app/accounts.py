"""Cuentas del servidor de sincronización.

Tres formas de entrar a una cuenta:

- usuario y contraseña (Basic Auth), lo que usa la app del móvil;
- un token de dispositivo, que recibe un dispositivo emparejado con un código de 6 cifras
  (el reloj: escribir una contraseña en él sería un suplicio);
- un token MCP, para el servidor MCP de un asistente. Solo vale si la cuenta tiene el acceso
  MCP activado en el panel, y deja de valer en cuanto se quita.

Contraseñas con scrypt; de los tokens y códigos solo se guarda su SHA-256.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import secrets
import sqlite3
import threading
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Optional

_N, _R, _P = 2**14, 8, 1

DEVICE = "device"
MCP = "mcp"


def hash_password(password: str) -> str:
    salt = secrets.token_bytes(16)
    dk = hashlib.scrypt(password.encode(), salt=salt, n=_N, r=_R, p=_P, dklen=32)
    return "$".join(["scrypt", str(_N), str(_R), str(_P), _b64(salt), _b64(dk)])


def verify_password(password: str, stored: str) -> bool:
    try:
        scheme, n, r, p, salt, expected = stored.split("$")
        if scheme != "scrypt":
            return False
        dk = hashlib.scrypt(
            password.encode(),
            salt=base64.b64decode(salt),
            n=int(n),
            r=int(r),
            p=int(p),
            dklen=len(base64.b64decode(expected)),
        )
    except (ValueError, TypeError):
        return False
    return hmac.compare_digest(dk, base64.b64decode(expected))


def _b64(raw: bytes) -> str:
    return base64.b64encode(raw).decode("ascii")


def sha256(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def _in(minutes: float) -> str:
    return (datetime.now(timezone.utc) + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")


@dataclass(frozen=True)
class Account:
    id: int
    username: str
    is_active: bool
    is_admin: bool = False
    mcp_enabled: bool = False
    password_is_default: bool = False
    note: Optional[str] = None
    food_account: Optional[str] = None
    password_hash: str = ""
    created_at: str = ""
    # Cómo entró esta petición: "password", "device" o "mcp".
    via: str = "password"

    @staticmethod
    def from_row(row: sqlite3.Row, via: str = "password") -> "Account":
        keys = row.keys()
        return Account(
            id=row["id"],
            username=row["username"],
            is_active=bool(row["is_active"]),
            is_admin=bool(row["is_admin"]) if "is_admin" in keys else False,
            mcp_enabled=bool(row["mcp_enabled"]) if "mcp_enabled" in keys else False,
            password_is_default=bool(row["password_is_default"])
            if "password_is_default" in keys
            else False,
            note=row["note"] if "note" in keys else None,
            food_account=row["food_account"] if "food_account" in keys else None,
            password_hash=row["password_hash"],
            created_at=row["created_at"],
            via=via,
        )


class AccountError(ValueError):
    """Un cambio que no se puede hacer; el mensaje se enseña tal cual."""


def _check_username(username: str) -> str:
    username = (username or "").strip()
    if (
        not username
        or len(username) > 64
        or ":" in username
        or not username.isascii()
        or any(c.isspace() for c in username)
    ):
        raise AccountError("Usuario: solo ASCII, sin espacios ni ':', hasta 64 caracteres")
    return username


def _check_password(password: str) -> None:
    if not password or not password.isascii():
        raise AccountError("Contraseña: no vacía y solo ASCII (Basic Auth)")


def get(conn: sqlite3.Connection, account_id: int) -> Optional[Account]:
    row = conn.execute("SELECT * FROM accounts WHERE id = ?", (account_id,)).fetchone()
    return Account.from_row(row) if row else None


def get_by_name(conn: sqlite3.Connection, username: str) -> Optional[Account]:
    row = conn.execute("SELECT * FROM accounts WHERE username = ?", (username,)).fetchone()
    return Account.from_row(row) if row else None


def create(
    conn: sqlite3.Connection,
    username: str,
    password: str,
    *,
    is_admin: bool = False,
    note: Optional[str] = None,
    password_is_default: bool = False,
) -> int:
    username = _check_username(username)
    _check_password(password)
    if get_by_name(conn, username):
        raise AccountError(f"Ya existe la cuenta {username}")
    cur = conn.execute(
        "INSERT INTO accounts (username, password_hash, created_at, is_admin, note, "
        "password_is_default) VALUES (?, ?, ?, ?, ?, ?)",
        (
            username,
            hash_password(password),
            now_iso(),
            int(is_admin),
            note or None,
            int(password_is_default),
        ),
    )
    return int(cur.lastrowid)


def set_password(conn: sqlite3.Connection, username: str, password: str) -> None:
    _check_password(password)
    cur = conn.execute(
        "UPDATE accounts SET password_hash = ?, password_is_default = 0 WHERE username = ?",
        (hash_password(password), username),
    )
    if cur.rowcount == 0:
        raise AccountError(f"No existe la cuenta {username}")
    forget_verified()


def set_active(conn: sqlite3.Connection, username: str, active: bool) -> None:
    cur = conn.execute(
        "UPDATE accounts SET is_active = ? WHERE username = ?", (int(active), username)
    )
    if cur.rowcount == 0:
        raise AccountError(f"No existe la cuenta {username}")


def update_flags(
    conn: sqlite3.Connection,
    account_id: int,
    *,
    acting_id: int,
    is_active: bool,
    is_admin: bool,
    mcp_enabled: bool,
    note: Optional[str],
    food_account: Optional[str] = None,
) -> None:
    if account_id == acting_id and (not is_active or not is_admin):
        raise AccountError("No puedes quitarte a ti mismo el acceso al panel")
    current = get(conn, account_id)
    if current is None:
        raise AccountError("Esa cuenta no existe")
    if current.is_admin and current.is_active and not (is_admin and is_active):
        others = conn.execute(
            "SELECT COUNT(*) c FROM accounts WHERE is_admin = 1 AND is_active = 1 AND id != ?",
            (account_id,),
        ).fetchone()["c"]
        if others == 0:
            raise AccountError("Tiene que quedar al menos un administrador activo")
    food_account = (food_account or "").strip() or None
    if food_account and (len(food_account) > 64 or not food_account.isascii() or any(c.isspace() for c in food_account)):
        raise AccountError("Cuenta de foods: el nombre de usuario tal cual, sin espacios")
    conn.execute(
        "UPDATE accounts SET is_active = ?, is_admin = ?, mcp_enabled = ?, note = ?, food_account = ? WHERE id = ?",
        (int(is_active), int(is_admin), int(mcp_enabled), note or None, food_account, account_id),
    )
    if not is_active:
        conn.execute("DELETE FROM admin_sessions WHERE account_id = ?", (account_id,))


def delete(conn: sqlite3.Connection, account_id: int, *, acting_id: int) -> None:
    if account_id == acting_id:
        raise AccountError("No puedes borrarte a ti mismo")
    conn.execute("DELETE FROM accounts WHERE id = ?", (account_id,))


def bootstrap(conn: sqlite3.Connection, settings) -> list[str]:
    """El primer administrador del panel, si no hay ninguno (contraseña provisional)."""
    done = []
    admins = conn.execute("SELECT COUNT(*) c FROM accounts WHERE is_admin = 1").fetchone()["c"]
    if admins == 0:
        existing = get_by_name(conn, settings.admin_username)
        if existing is None:
            create(
                conn,
                settings.admin_username,
                settings.admin_initial_password,
                is_admin=True,
                note="Administrador",
                password_is_default=True,
            )
            done.append(f"Creado el administrador {settings.admin_username} (contraseña provisional)")
        else:
            conn.execute("UPDATE accounts SET is_admin = 1 WHERE id = ?", (existing.id,))
            done.append(f"{settings.admin_username} pasa a ser administrador")
    return done


# --- Contraseñas: comprobación con memoria ------------------------------------------
# La app manda la contraseña en cada sincronización y scrypt cuesta ~100 ms en la Pi a
# propósito: se recuerda un rato que el par era bueno. Solo eso: los permisos se leen de
# la base de datos en cada petición.

_TTL = 600
_verified: dict[tuple[str, str], tuple[int, float]] = {}
_lock = threading.Lock()


def forget_verified() -> None:
    with _lock:
        _verified.clear()


def authenticate(conn: sqlite3.Connection, username: str, password: str) -> Optional[Account]:
    key = (username.lower(), sha256(password))
    with _lock:
        hit = _verified.get(key)
    if hit is not None and hit[1] > time.monotonic():
        return get(conn, hit[0])
    account = get_by_name(conn, username)
    if account is None or not verify_password(password, account.password_hash):
        return None
    with _lock:
        _verified[key] = (account.id, time.monotonic() + _TTL)
    return account


# --- Tokens -------------------------------------------------------------------------


def create_token(conn: sqlite3.Connection, account_id: int, kind: str, name: str) -> str:
    """Devuelve el token en claro: es la única vez que se puede ver."""
    token = f"fy_{kind}_{secrets.token_urlsafe(32)}"
    conn.execute(
        "INSERT INTO tokens (account_id, kind, name, token_hash, created_at) VALUES (?, ?, ?, ?, ?)",
        (account_id, kind, (name or kind)[:80], sha256(token), now_iso()),
    )
    return token


def authenticate_token(conn: sqlite3.Connection, token: str) -> Optional[Account]:
    row = conn.execute(
        "SELECT t.id tid, t.kind, a.* FROM tokens t JOIN accounts a ON a.id = t.account_id "
        "WHERE t.token_hash = ? AND t.revoked = 0 AND (t.expires_at IS NULL OR t.expires_at > ?)",
        (sha256(token), now_iso()),
    ).fetchone()
    if row is None:
        return None
    if row["kind"] == MCP and not row["mcp_enabled"]:
        return None
    conn.execute("UPDATE tokens SET last_used_at = ? WHERE id = ?", (now_iso(), row["tid"]))
    return Account.from_row(row, via=row["kind"])


def list_tokens(conn: sqlite3.Connection, account_id: int) -> list[sqlite3.Row]:
    return conn.execute(
        "SELECT id, kind, name, created_at, last_used_at, revoked FROM tokens "
        "WHERE account_id = ? ORDER BY revoked, created_at DESC",
        (account_id,),
    ).fetchall()


def revoke_token(conn: sqlite3.Connection, account_id: int, token_id: int) -> None:
    conn.execute(
        "UPDATE tokens SET revoked = 1 WHERE id = ? AND account_id = ?", (token_id, account_id)
    )


# --- Emparejar dispositivos ---------------------------------------------------------

PAIRING_MINUTES = 10


def create_pairing_code(conn: sqlite3.Connection, account_id: int) -> str:
    code = f"{secrets.randbelow(1_000_000):06d}"
    conn.execute("DELETE FROM pairing_codes WHERE expires_at < ?", (now_iso(),))
    conn.execute(
        "INSERT OR REPLACE INTO pairing_codes (code_hash, account_id, expires_at) VALUES (?, ?, ?)",
        (sha256(code), account_id, _in(PAIRING_MINUTES)),
    )
    return code


def redeem_pairing_code(conn: sqlite3.Connection, code: str, name: str) -> Optional[tuple[Account, str]]:
    """Un código vale una vez y durante unos minutos: a cambio, un token de dispositivo."""
    row = conn.execute(
        "SELECT account_id FROM pairing_codes WHERE code_hash = ? AND used = 0 AND expires_at >= ?",
        (sha256(code.strip()), now_iso()),
    ).fetchone()
    if row is None:
        return None
    conn.execute("UPDATE pairing_codes SET used = 1 WHERE code_hash = ?", (sha256(code.strip()),))
    account = get(conn, row["account_id"])
    if account is None or not account.is_active:
        return None
    return account, create_token(conn, account.id, DEVICE, name or "Dispositivo")


# --- Sesiones del panel -------------------------------------------------------------


def create_session(conn: sqlite3.Connection, account_id: int, hours: int) -> str:
    token = secrets.token_urlsafe(32)
    conn.execute("DELETE FROM admin_sessions WHERE expires_at < ?", (now_iso(),))
    conn.execute(
        "INSERT INTO admin_sessions (token_hash, account_id, expires_at) VALUES (?, ?, ?)",
        (sha256(token), account_id, _in(hours * 60)),
    )
    return token


def session_account(conn: sqlite3.Connection, token: str) -> Optional[Account]:
    row = conn.execute(
        "SELECT a.* FROM admin_sessions s JOIN accounts a ON a.id = s.account_id "
        "WHERE s.token_hash = ? AND s.expires_at > ? AND a.is_admin = 1 AND a.is_active = 1",
        (sha256(token), now_iso()),
    ).fetchone()
    return Account.from_row(row) if row else None


def delete_session(conn: sqlite3.Connection, token: str) -> None:
    conn.execute("DELETE FROM admin_sessions WHERE token_hash = ?", (sha256(token),))
