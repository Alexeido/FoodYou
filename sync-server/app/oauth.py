"""OAuth 2.1 para el MCP: lo que pide claude.ai para añadirlo como conector personalizado.

El flujo, como lo describe la especificación de autorización de MCP:

1. Claude llama a /mcp sin token; el MCP contesta 401 y señala
   /.well-known/oauth-protected-resource, que dice que este servidor es quien autoriza.
2. Claude lee /.well-known/oauth-authorization-server y se registra solo en /oauth/register.
3. La persona entra en /oauth/authorize con su usuario de sincronización y pulsa "Permitir".
   Solo se puede si la cuenta tiene el acceso MCP activado en el panel.
4. Claude cambia el código por un token en /oauth/token (con PKCE) y lo renueva con el token
   de refresco cuando caduca.

El token resultante es un token MCP más: sale en el panel de la cuenta y se revoca desde ahí.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import threading
import time
from typing import Optional
from urllib.parse import urlencode, urlsplit

from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, JSONResponse, RedirectResponse, Response

from app import accounts
from app.accounts import MCP, _in, now_iso, sha256
from app.admin.router import templates
from app.config import get_settings
from app.db import connect, transaction

router = APIRouter()

CODE_MINUTES = 5
SCOPE = "diary"

# Intentos de contraseña fallidos y registros de clientes, por IP.
_failures: dict[str, list[float]] = {}
_registrations: dict[str, list[float]] = {}
_lock = threading.Lock()


def _base() -> str:
    return get_settings().public_url.rstrip("/")


def _ip(request: Request) -> str:
    return request.headers.get("cf-connecting-ip") or (request.client.host if request.client else "?")


def _too_many(bucket: dict[str, list[float]], ip: str, limit: int, window: float, add: bool = False) -> bool:
    now = time.monotonic()
    with _lock:
        recent = [t for t in bucket.get(ip, []) if now - t < window]
        if add:
            recent.append(now)
        bucket[ip] = recent
        return len(recent) > limit if add else len(recent) >= limit


def _no_store(body: dict, status: int = 200) -> JSONResponse:
    return JSONResponse(body, status_code=status, headers={"Cache-Control": "no-store", "Pragma": "no-cache"})


def _error(code: str, description: str, status: int = 400) -> JSONResponse:
    return _no_store({"error": code, "error_description": description}, status)


# --- Descubrimiento -----------------------------------------------------------------


@router.get("/.well-known/oauth-protected-resource")
@router.get("/.well-known/oauth-protected-resource/mcp")
def protected_resource() -> dict:
    base = _base()
    return {
        "resource": f"{base}/mcp",
        "authorization_servers": [base],
        "bearer_methods_supported": ["header"],
        "scopes_supported": [SCOPE],
        "resource_name": "Food You",
    }


@router.get("/.well-known/oauth-authorization-server")
def authorization_server() -> dict:
    base = _base()
    return {
        "issuer": base,
        "authorization_endpoint": f"{base}/oauth/authorize",
        "token_endpoint": f"{base}/oauth/token",
        "registration_endpoint": f"{base}/oauth/register",
        "scopes_supported": [SCOPE],
        "response_types_supported": ["code"],
        "grant_types_supported": ["authorization_code", "refresh_token"],
        "code_challenge_methods_supported": ["S256"],
        "token_endpoint_auth_methods_supported": ["none", "client_secret_post", "client_secret_basic"],
    }


# --- Registro de clientes -----------------------------------------------------------


def _valid_redirect(uri: object) -> bool:
    if not isinstance(uri, str) or len(uri) > 500:
        return False
    parts = urlsplit(uri)
    if parts.fragment:
        return False
    if parts.scheme == "https" and parts.hostname:
        return True
    # Clientes de escritorio que esperan la respuesta en local.
    return parts.scheme == "http" and parts.hostname in ("localhost", "127.0.0.1")


@router.post("/oauth/register")
async def register(request: Request) -> JSONResponse:
    if _too_many(_registrations, _ip(request), 20, 3600, add=True):
        return _error("invalid_client_metadata", "Demasiados registros; espera un rato", 429)
    raw = await request.body()
    if len(raw) > 16 * 1024:
        return _error("invalid_client_metadata", "Petición demasiado grande")
    try:
        body = json.loads(raw or b"{}")
    except json.JSONDecodeError:
        return _error("invalid_client_metadata", "JSON no válido")
    if not isinstance(body, dict):
        return _error("invalid_client_metadata", "El cuerpo tiene que ser un objeto")

    uris = body.get("redirect_uris")
    if not isinstance(uris, list) or not 1 <= len(uris) <= 10 or not all(_valid_redirect(u) for u in uris):
        return _error("invalid_redirect_uri", "redirect_uris: entre 1 y 10, https (o http a localhost)")
    name = str(body.get("client_name") or "Cliente MCP").strip()[:60] or "Cliente MCP"
    method = body.get("token_endpoint_auth_method") or "none"
    if method not in ("none", "client_secret_post", "client_secret_basic"):
        method = "none"

    client_id = "fyc_" + secrets.token_urlsafe(16)
    secret = "fycs_" + secrets.token_urlsafe(32) if method != "none" else None
    issued = int(time.time())
    with transaction() as conn:
        conn.execute(
            "INSERT INTO oauth_clients (client_id, secret_hash, name, redirect_uris, created_at) VALUES (?, ?, ?, ?, ?)",
            (client_id, sha256(secret) if secret else None, name, json.dumps(uris), now_iso()),
        )
    response = {
        "client_id": client_id,
        "client_id_issued_at": issued,
        "client_name": name,
        "redirect_uris": uris,
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": method,
        "scope": SCOPE,
    }
    if secret:
        response["client_secret"] = secret
        response["client_secret_expires_at"] = 0
    return _no_store(response, 201)


def _client(conn, client_id: str) -> Optional[dict]:
    row = conn.execute("SELECT * FROM oauth_clients WHERE client_id = ?", (client_id,)).fetchone()
    if row is None:
        return None
    return {"id": row["client_id"], "name": row["name"], "secret_hash": row["secret_hash"],
            "redirect_uris": json.loads(row["redirect_uris"])}


# --- Autorizar ----------------------------------------------------------------------

_PARAMS = ("client_id", "redirect_uri", "state", "code_challenge", "code_challenge_method", "scope", "resource")


def _page(request: Request, params: dict, client: dict, *, error: str = "", username: str = "", status: int = 200) -> HTMLResponse:
    return templates.TemplateResponse(
        request,
        "oauth_authorize.html",
        {"params": params, "client": client, "error": error, "username": username,
         "redirect_host": urlsplit(params["redirect_uri"]).hostname},
        status_code=status,
    )


def _broken(request: Request, message: str) -> HTMLResponse:
    """Errores en los que no se puede volver al cliente: se enseñan aquí."""
    return templates.TemplateResponse(
        request, "oauth_authorize.html", {"broken": message}, status_code=400
    )


def _back(redirect_uri: str, **values: Optional[str]) -> RedirectResponse:
    query = urlencode({k: v for k, v in values.items() if v is not None})
    separator = "&" if "?" in redirect_uri else "?"
    return RedirectResponse(f"{redirect_uri}{separator}{query}", status_code=303)


def _check(request: Request, params: dict):
    """El cliente y la vuelta; luego, los errores que sí se pueden devolver al cliente."""
    conn = connect()
    try:
        client = _client(conn, params.get("client_id") or "")
    finally:
        conn.close()
    if client is None:
        return None, _broken(request, "Esta aplicación no está registrada. Vuelve a añadir el conector desde Claude.")
    if params.get("redirect_uri") not in client["redirect_uris"]:
        return None, _broken(request, "La dirección de vuelta no coincide con la registrada.")
    if not params.get("code_challenge") or params.get("code_challenge_method") != "S256":
        return None, _back(params["redirect_uri"], error="invalid_request",
                           error_description="Hace falta PKCE con S256", state=params.get("state"),
                           iss=_base())
    return client, None


@router.get("/oauth/authorize")
def authorize_page(request: Request):
    params = {k: request.query_params.get(k) or "" for k in _PARAMS}
    if request.query_params.get("response_type") != "code":
        if params["client_id"] and params["redirect_uri"]:
            client, problem = _check(request, params)
            if problem:
                return problem
            return _back(params["redirect_uri"], error="unsupported_response_type",
                         state=params["state"] or None, iss=_base())
        return _broken(request, "Petición de autorización incompleta.")
    client, problem = _check(request, params)
    if problem:
        return problem
    return _page(request, params, client)


@router.post("/oauth/authorize")
async def authorize(request: Request):
    form = await request.form()
    params = {k: str(form.get(k) or "") for k in _PARAMS}
    client, problem = _check(request, params)
    if problem:
        return problem
    state = params["state"] or None

    if form.get("decision") != "allow":
        return _back(params["redirect_uri"], error="access_denied", state=state, iss=_base())

    ip = _ip(request)
    username = str(form.get("username") or "").strip()
    if _too_many(_failures, ip, 10, 600):
        return _page(request, params, client, username=username, status=429,
                     error="Demasiados intentos fallidos. Espera unos minutos.")
    conn = connect()
    try:
        account = accounts.authenticate(conn, username, str(form.get("password") or ""))
    finally:
        conn.close()
    if account is None:
        _too_many(_failures, ip, 10, 600, add=True)
        return _page(request, params, client, username=username, status=401,
                     error="Usuario o contraseña incorrectos.")
    if not account.is_active:
        return _page(request, params, client, username=username, status=403, error="Esta cuenta está desactivada.")
    if not account.mcp_enabled:
        return _page(request, params, client, username=username, status=403,
                     error="Tu cuenta no tiene el acceso MCP activado. Pídeselo a quien administra el servidor.")

    code = secrets.token_urlsafe(32)
    with transaction() as conn:
        conn.execute("DELETE FROM oauth_codes WHERE expires_at < ? OR used = 1", (now_iso(),))
        conn.execute(
            "INSERT INTO oauth_codes (code_hash, client_id, account_id, redirect_uri, code_challenge, expires_at) "
            "VALUES (?, ?, ?, ?, ?, ?)",
            (sha256(code), client["id"], account.id, params["redirect_uri"], params["code_challenge"],
             _in(CODE_MINUTES)),
        )
    return _back(params["redirect_uri"], code=code, state=state, iss=_base())


# --- Tokens -------------------------------------------------------------------------


def _client_credentials(request: Request, form) -> tuple[str, Optional[str]]:
    header = request.headers.get("authorization", "")
    if header.lower().startswith("basic "):
        try:
            client_id, _, secret = base64.b64decode(header[6:]).decode().partition(":")
            return client_id, secret
        except ValueError:
            return "", None
    return str(form.get("client_id") or ""), (str(form["client_secret"]) if form.get("client_secret") else None)


def _issue(conn, *, account_id: int, client: dict, token_id: Optional[int] = None) -> dict:
    """Un token nuevo y su refresco. Al renovar se reutiliza la fila: en el panel sale una
    sola entrada por conector, y revocarla corta las dos cosas."""
    hours = get_settings().oauth_access_hours
    access = f"fy_{MCP}_{secrets.token_urlsafe(32)}"
    refresh = f"fy_refresh_{secrets.token_urlsafe(32)}"
    if token_id is None:
        conn.execute(
            "INSERT INTO tokens (account_id, kind, name, token_hash, created_at, expires_at, refresh_hash, client_id) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (account_id, MCP, f"{client['name']} (conector)"[:80], sha256(access), now_iso(), _in(hours * 60),
             sha256(refresh), client["id"]),
        )
    else:
        conn.execute(
            "UPDATE tokens SET token_hash = ?, refresh_hash = ?, expires_at = ? WHERE id = ?",
            (sha256(access), sha256(refresh), _in(hours * 60), token_id),
        )
    return {"access_token": access, "token_type": "Bearer", "expires_in": hours * 3600,
            "refresh_token": refresh, "scope": SCOPE}


@router.post("/oauth/token")
async def token(request: Request) -> Response:
    form = await request.form()
    client_id, secret = _client_credentials(request, form)
    conn = connect()
    try:
        client = _client(conn, client_id)
    finally:
        conn.close()
    if client is None:
        return _error("invalid_client", "Cliente desconocido", 401)
    if client["secret_hash"] and not (secret and hmac.compare_digest(client["secret_hash"], sha256(secret))):
        return _error("invalid_client", "Secreto del cliente incorrecto", 401)

    grant = form.get("grant_type")
    if grant == "authorization_code":
        code = str(form.get("code") or "")
        verifier = str(form.get("code_verifier") or "")
        with transaction() as conn:
            row = conn.execute(
                "SELECT c.*, a.is_active, a.mcp_enabled FROM oauth_codes c JOIN accounts a ON a.id = c.account_id "
                "WHERE c.code_hash = ?",
                (sha256(code),),
            ).fetchone()
            if row is None or row["used"] or row["expires_at"] < now_iso() or row["client_id"] != client["id"]:
                return _error("invalid_grant", "Código no válido, caducado o ya usado")
            conn.execute("UPDATE oauth_codes SET used = 1 WHERE code_hash = ?", (sha256(code),))
            if form.get("redirect_uri") and form.get("redirect_uri") != row["redirect_uri"]:
                return _error("invalid_grant", "redirect_uri no coincide")
            expected = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
            if not verifier or not hmac.compare_digest(expected, row["code_challenge"]):
                return _error("invalid_grant", "PKCE: code_verifier no coincide")
            if not row["is_active"] or not row["mcp_enabled"]:
                return _error("invalid_grant", "La cuenta no tiene acceso MCP")
            return _no_store(_issue(conn, account_id=row["account_id"], client=client))

    if grant == "refresh_token":
        refresh = str(form.get("refresh_token") or "")
        with transaction() as conn:
            row = conn.execute(
                "SELECT t.id, t.account_id, t.client_id, a.is_active, a.mcp_enabled FROM tokens t "
                "JOIN accounts a ON a.id = t.account_id WHERE t.refresh_hash = ? AND t.revoked = 0",
                (sha256(refresh),),
            ).fetchone()
            if row is None or row["client_id"] != client["id"]:
                return _error("invalid_grant", "Token de refresco no válido o revocado")
            if not row["is_active"] or not row["mcp_enabled"]:
                return _error("invalid_grant", "La cuenta no tiene acceso MCP")
            return _no_store(_issue(conn, account_id=row["account_id"], client=client, token_id=row["id"]))

    return _error("unsupported_grant_type", "Solo authorization_code y refresh_token")
