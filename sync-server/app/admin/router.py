"""Panel /admin del servidor de sincronización.

No gestiona el contenido de los diarios: enseña quién sincroniza, desde qué dispositivos y
cuánto, y deja crear cuentas, cambiar contraseñas, desactivarlas y dar o quitar el acceso
MCP (con sus tokens).

Mismas defensas que el panel del servidor de alimentos: cookie HttpOnly SameSite=Strict,
todo POST comprobado contra Origin, y bloqueo de 15 minutos tras 5 contraseñas fallidas.
"""

from __future__ import annotations

import hashlib
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional
from urllib.parse import urlparse
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Form, Request
from fastapi.responses import HTMLResponse, RedirectResponse, Response
from fastapi.templating import Jinja2Templates

from app import accounts, activity
from app.accounts import AccountError
from app.config import get_settings
from app.db import connect, transaction

router = APIRouter(prefix="/admin")
STATIC_DIR = Path(__file__).parent / "static"
templates = Jinja2Templates(directory=str(Path(__file__).parent / "templates"))
COOKIE = "fy_sync_admin"


def _parse(value: str) -> datetime:
    if value.endswith("Z"):
        value = value[:-1] + "+00:00"
    dt = datetime.fromisoformat(value)
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _local(value: Optional[str], fmt: str = "%d/%m/%Y %H:%M") -> str:
    if not value:
        return "—"
    return _parse(value).astimezone(ZoneInfo(get_settings().display_timezone)).strftime(fmt)


def _ago(value: Optional[str]) -> str:
    if not value:
        return "nunca"
    seconds = (datetime.now(timezone.utc) - _parse(value)).total_seconds()
    if seconds < 60:
        return "ahora mismo"
    if seconds < 3600:
        return f"hace {int(seconds // 60)} min"
    if seconds < 86400:
        return f"hace {int(seconds // 3600)} h"
    days = int(seconds // 86400)
    return "ayer" if days == 1 else f"hace {days} días"


def _asset_version() -> str:
    digest = hashlib.sha256()
    for name in ("admin.css", "admin.js"):
        digest.update((STATIC_DIR / name).read_bytes())
    return digest.hexdigest()[:10]


templates.env.filters["local"] = _local
templates.env.filters["ago"] = _ago
templates.env.globals["asset_version"] = _asset_version()

KIND_NAMES = {
    "meal": "Comidas",
    "food_entry": "Entradas del diario",
    "manual_entry": "Entradas rápidas",
    "recipe": "Recetas",
    "goals": "Objetivos",
    "memory": "Memoria del asistente",
}
templates.env.globals["kind_names"] = KIND_NAMES


# --- Sesión -------------------------------------------------------------------------


class LoginRequired(Exception):
    pass


def current_admin(request: Request) -> accounts.Account:
    token = request.cookies.get(COOKIE)
    if token:
        conn = connect()
        try:
            account = accounts.session_account(conn, token)
        finally:
            conn.close()
        if account is not None:
            return account
    raise LoginRequired()


def _same_origin(request: Request) -> bool:
    origin = request.headers.get("origin") or request.headers.get("referer")
    return bool(origin) and urlparse(origin).netloc == request.headers.get("host")


def _forbidden() -> Response:
    return HTMLResponse("Petición rechazada: no viene de este panel.", status_code=403)


def _secure(request: Request) -> bool:
    return request.url.scheme == "https" or request.headers.get("x-forwarded-proto") == "https"


_failures: dict[str, list[float]] = {}
_failures_lock = threading.Lock()


def _ip(request: Request) -> str:
    return request.headers.get("cf-connecting-ip") or (request.client.host if request.client else "?")


def _locked(ip: str) -> bool:
    now = time.monotonic()
    with _failures_lock:
        recent = [t for t in _failures.get(ip, []) if now - t < 900]
        _failures[ip] = recent
        return len(recent) >= 5


def _fail(ip: str) -> None:
    with _failures_lock:
        _failures.setdefault(ip, []).append(time.monotonic())


def _page(request: Request, name: str, context: dict, status_code: int = 200) -> Response:
    return templates.TemplateResponse(
        request, name, {"flash": request.query_params.get("ok"), **context}, status_code=status_code
    )


# --- Login --------------------------------------------------------------------------


@router.get("/login", response_class=HTMLResponse)
def login_page(request: Request) -> Response:
    return _page(request, "login.html", {"error": None, "username": ""})


@router.post("/login", response_class=HTMLResponse)
def login(request: Request, username: str = Form(""), password: str = Form("")) -> Response:
    if not _same_origin(request):
        return _forbidden()
    ip = _ip(request)
    if _locked(ip):
        return _page(
            request, "login.html", {"error": "Demasiados intentos. Espera 15 minutos.", "username": username}, 429
        )
    settings = get_settings()
    token = None
    with transaction() as conn:
        account = accounts.get_by_name(conn, username.strip())
        if (
            account is not None
            and account.is_admin
            and account.is_active
            and accounts.verify_password(password, account.password_hash)
        ):
            token = accounts.create_session(conn, account.id, settings.admin_session_hours)
    if token is None:
        _fail(ip)
        return _page(
            request, "login.html", {"error": "Usuario o contraseña incorrectos.", "username": username}, 401
        )
    response = RedirectResponse("/admin", status_code=303)
    response.set_cookie(
        COOKIE,
        token,
        max_age=settings.admin_session_hours * 3600,
        httponly=True,
        samesite="strict",
        secure=_secure(request),
        path="/admin",
    )
    return response


@router.post("/logout")
def logout(request: Request) -> Response:
    if not _same_origin(request):
        return _forbidden()
    token = request.cookies.get(COOKIE)
    if token:
        with transaction() as conn:
            accounts.delete_session(conn, token)
    response = RedirectResponse("/admin/login", status_code=303)
    response.delete_cookie(COOKIE, path="/admin")
    return response


# --- Cuentas ------------------------------------------------------------------------


def _series(points: list[tuple[str, int]]) -> dict:
    """Formato de la macro de barras del panel (charts.html)."""
    return {
        "points": [{"label": f"{d[8:10]}/{d[5:7]}", "value": n} for d, n in points],
        "peak": max((n for _, n in points), default=0),
    }


@router.get("", response_class=HTMLResponse)
def dashboard(request: Request) -> Response:
    admin = current_admin(request)
    conn = connect()
    try:
        context = {
            "admin": admin,
            "section": "users",
            "rows": activity.overview(conn),
            "totals": activity.totals(conn),
            "daily": _series(activity.daily_syncs(conn)),
        }
    finally:
        conn.close()
    return _page(request, "dashboard.html", context)


@router.get("/accounts/new", response_class=HTMLResponse)
def new_account_page(request: Request) -> Response:
    admin = current_admin(request)
    return _page(request, "new_account.html", {"admin": admin, "section": "users", "error": None, "form": {}})


@router.post("/accounts/new", response_class=HTMLResponse)
def create_account(
    request: Request,
    username: str = Form(""),
    password: str = Form(""),
    note: str = Form(""),
) -> Response:
    admin = current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    try:
        with transaction() as conn:
            account_id = accounts.create(conn, username, password, note=note.strip())
    except AccountError as exc:
        return _page(
            request,
            "new_account.html",
            {"admin": admin, "section": "users", "error": str(exc), "form": {"username": username, "note": note}},
            400,
        )
    return RedirectResponse(f"/admin/accounts/{account_id}?ok=Cuenta creada", status_code=303)


def _account_page(request: Request, admin, account_id: int, *, error=None, new_token=None, status_code=200):
    conn = connect()
    try:
        account = accounts.get(conn, account_id)
        if account is None:
            return RedirectResponse("/admin", status_code=303)
        context = {
            "admin": admin,
            "section": "users",
            "account": account,
            "error": error,
            "new_token": new_token,
            "devices": activity.devices(conn, account_id),
            "tokens": accounts.list_tokens(conn, account_id),
            "recent": activity.recent(conn, account_id),
            "kinds": activity.documents_by_kind(conn, account_id),
            "daily": _series(activity.daily_syncs(conn, account_id)),
            "mcp_url": get_settings().mcp_public_url,
        }
    finally:
        conn.close()
    return _page(request, "account_detail.html", context, status_code)


@router.get("/accounts/{account_id}", response_class=HTMLResponse)
def account_page(request: Request, account_id: int) -> Response:
    return _account_page(request, current_admin(request), account_id)


@router.post("/accounts/{account_id}", response_class=HTMLResponse)
def update_account(
    request: Request,
    account_id: int,
    note: str = Form(""),
    password: str = Form(""),
    is_active: Optional[str] = Form(None),
    is_admin: Optional[str] = Form(None),
    mcp_enabled: Optional[str] = Form(None),
    food_account: str = Form(""),
) -> Response:
    admin = current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    try:
        with transaction() as conn:
            accounts.update_flags(
                conn,
                account_id,
                acting_id=admin.id,
                is_active=is_active is not None,
                is_admin=is_admin is not None,
                mcp_enabled=mcp_enabled is not None,
                note=note.strip(),
                food_account=food_account,
            )
            if password:
                account = accounts.get(conn, account_id)
                accounts.set_password(conn, account.username, password)
    except AccountError as exc:
        return _account_page(request, admin, account_id, error=str(exc), status_code=400)
    return RedirectResponse(f"/admin/accounts/{account_id}?ok=Guardado", status_code=303)


@router.post("/accounts/{account_id}/mcp-token", response_class=HTMLResponse)
def create_mcp_token(request: Request, account_id: int, name: str = Form("")) -> Response:
    admin = current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    with transaction() as conn:
        account = accounts.get(conn, account_id)
        if account is None:
            return RedirectResponse("/admin", status_code=303)
        if not account.mcp_enabled:
            return _account_page(
                request, admin, account_id, error="Activa primero el acceso MCP de la cuenta.", status_code=400
            )
        token = accounts.create_token(conn, account_id, accounts.MCP, name.strip() or "Claude")
    # El token se enseña una sola vez, en esta respuesta; no se guarda en claro.
    return _account_page(request, admin, account_id, new_token=token)


@router.post("/accounts/{account_id}/tokens/{token_id}/revoke")
def revoke_token(request: Request, account_id: int, token_id: int) -> Response:
    current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    with transaction() as conn:
        accounts.revoke_token(conn, account_id, token_id)
    return RedirectResponse(f"/admin/accounts/{account_id}?ok=Acceso retirado", status_code=303)


@router.post("/accounts/{account_id}/delete")
def delete_account(request: Request, account_id: int) -> Response:
    admin = current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    try:
        with transaction() as conn:
            account = accounts.get(conn, account_id)
            accounts.delete(conn, account_id, acting_id=admin.id)
    except AccountError as exc:
        return _account_page(request, admin, account_id, error=str(exc), status_code=400)
    name = account.username if account else ""
    return RedirectResponse(f"/admin?ok=Borrada la cuenta {name}", status_code=303)


# --- Mi cuenta ----------------------------------------------------------------------


@router.get("/account", response_class=HTMLResponse)
def my_account(request: Request) -> Response:
    admin = current_admin(request)
    return _page(request, "account.html", {"admin": admin, "section": "account", "error": None})


@router.post("/account", response_class=HTMLResponse)
def change_password(
    request: Request, current: str = Form(""), new: str = Form(""), repeat: str = Form("")
) -> Response:
    admin = current_admin(request)
    if not _same_origin(request):
        return _forbidden()
    error = None
    if not accounts.verify_password(current, admin.password_hash):
        error = "La contraseña actual no es correcta."
    elif new != repeat:
        error = "Las dos contraseñas nuevas no coinciden."
    elif len(new) < 10:
        error = "Usa al menos 10 caracteres: este panel está abierto a internet."
    else:
        try:
            with transaction() as conn:
                accounts.set_password(conn, admin.username, new)
        except AccountError as exc:
            error = str(exc)
    if error:
        return _page(request, "account.html", {"admin": admin, "section": "account", "error": error}, 400)
    conn = connect()
    try:
        admin = accounts.get(conn, admin.id)
    finally:
        conn.close()
    return templates.TemplateResponse(
        request,
        "account.html",
        {"admin": admin, "section": "account", "error": None, "flash": "Contraseña cambiada."},
    )
