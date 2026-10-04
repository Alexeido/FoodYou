"""Servidor de sincronización de Food You. El contrato está en docs/sync/protocol.md."""

from __future__ import annotations

import base64
import json
import logging
import re
import threading
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import JSONResponse, RedirectResponse, StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBasic, HTTPBasicCredentials
from fastapi.staticfiles import StaticFiles

from app import accounts, activity, store
from app.admin.router import STATIC_DIR, LoginRequired
from app.admin.router import router as admin_router
from app.oauth import router as oauth_router
from app.config import get_settings
from app.db import connect, init_schema, transaction
from app.events import notifier

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger(__name__)

_DEVICE = re.compile(r"^[A-Za-z0-9._-]{1,64}$")


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_schema()
    with transaction() as conn:
        for line in accounts.bootstrap(conn, get_settings()):
            log.warning(line)
        activity.purge(conn)
    log.info("Servidor de sincronización listo")
    yield


app = FastAPI(title="Food You sync", lifespan=lifespan)
app.include_router(admin_router)
app.include_router(oauth_router)
app.mount("/admin/static", StaticFiles(directory=STATIC_DIR), name="admin-static")


@app.exception_handler(LoginRequired)
def _login_required(request: Request, exc: LoginRequired) -> RedirectResponse:
    return RedirectResponse("/admin/login", status_code=303)


# --- Autenticación ------------------------------------------------------------------


def _unauthorized() -> HTTPException:
    return HTTPException(401, "Credenciales inválidas", headers={"WWW-Authenticate": "Basic"})


def require_account(request: Request) -> accounts.Account:
    """Basic Auth (la app) o Bearer con un token de dispositivo o MCP."""
    header = request.headers.get("authorization", "")
    conn = connect()
    try:
        if header.lower().startswith("bearer "):
            account = accounts.authenticate_token(conn, header[7:].strip())
        elif header.lower().startswith("basic "):
            try:
                username, _, password = base64.b64decode(header[6:]).decode("latin-1").partition(":")
            except ValueError:
                raise _unauthorized()
            account = accounts.authenticate(conn, username, password)
        else:
            account = None
    finally:
        conn.close()
    if account is None:
        raise _unauthorized()
    if not account.is_active:
        raise HTTPException(403, "Cuenta desactivada")
    return account


def _device(request: Request, account: accounts.Account) -> str:
    value = (request.headers.get("x-device-id") or "").strip()
    if _DEVICE.match(value):
        return value
    return "mcp" if account.via == accounts.MCP else "unknown"


# --- Rutas --------------------------------------------------------------------------


@app.get("/health")
def health() -> dict:
    return {"status": "ok"}


@app.get("/v1/status")
def status(account: accounts.Account = Depends(require_account)) -> dict:
    conn = connect()
    try:
        # `access`: con qué entró (password, device o mcp). El MCP solo acepta tokens mcp.
        return {
            "account": account.username,
            "access": account.via,
            "foodAccount": account.food_account,
            **store.status(conn, account.id),
        }
    finally:
        conn.close()


def _sync_in_db(account: accounts.Account, changes, cursor: int, device: str, user_agent: str):
    settings = get_settings()
    with transaction() as conn:
        changed = store.apply_changes(conn, account.id, changes)
        documents, new_cursor, more = store.documents_since(
            conn, account.id, cursor, settings.max_documents_per_response
        )
        activity.record_sync(conn, account.id, device, user_agent, len(changes), len(documents))
    return changed, documents, new_cursor, more


@app.post("/v1/sync")
async def sync(request: Request, account: accounts.Account = Depends(require_account)):
    settings = get_settings()
    raw = await request.body()
    if len(raw) > settings.max_body_bytes:
        return JSONResponse({"detail": "Petición demasiado grande"}, status_code=413)
    try:
        body = json.loads(raw or b"{}")
    except json.JSONDecodeError:
        return JSONResponse({"detail": "JSON no válido"}, status_code=400)
    if not isinstance(body, dict):
        return JSONResponse({"detail": "El cuerpo tiene que ser un objeto"}, status_code=400)

    cursor = body.get("cursor", 0)
    if not isinstance(cursor, int) or isinstance(cursor, bool) or cursor < 0:
        return JSONResponse({"detail": "cursor tiene que ser un entero >= 0"}, status_code=400)

    device = _device(request, account)
    try:
        changes = store.parse_changes(
            body.get("changes", []),
            device,
            max_changes=settings.max_changes_per_request,
            max_doc_bytes=settings.max_document_bytes,
            max_skew_ms=settings.max_clock_skew_ms,
        )
    except store.TooLarge as exc:
        return JSONResponse({"detail": str(exc)}, status_code=413)
    except store.InvalidChange as exc:
        return JSONResponse({"detail": str(exc)}, status_code=400)

    changed, documents, new_cursor, more = await run_in_threadpool(
        _sync_in_db, account, changes, cursor, device, request.headers.get("user-agent", "")
    )
    if changed:
        await notifier.notify(account.id)
        log.info("%s: %d cambios desde %s", account.username, changed, device)
    return {"cursor": new_cursor, "more": more, "documents": documents}


@app.get("/v1/events")
async def events(request: Request, account: accounts.Account = Depends(require_account)):
    """Server-Sent Events: `changed` cada vez que cambia algo de la cuenta, y un latido
    cada poco para que ningún proxy corte la conexión por inactividad."""
    keepalive = get_settings().events_keepalive_seconds

    async def stream():
        seen = notifier.version(account.id)
        yield "event: ready\ndata: {}\n\n"
        while True:
            if await request.is_disconnected():
                return
            changed = await notifier.wait(account.id, seen, keepalive)
            if changed:
                seen = notifier.version(account.id)
                yield f"event: changed\ndata: {json.dumps({'version': seen})}\n\n"
            else:
                yield ": ping\n\n"

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.post("/v1/pairing-codes")
def pairing_code(account: accounts.Account = Depends(require_account)) -> dict:
    """Un código de 6 cifras para emparejar un dispositivo (el reloj) con esta cuenta."""
    if account.via == accounts.MCP:
        raise HTTPException(403, "Un token MCP no puede emparejar dispositivos")
    with transaction() as conn:
        code = accounts.create_pairing_code(conn, account.id)
    return {"code": code, "expiresInSeconds": accounts.PAIRING_MINUTES * 60}


# Freno a probar códigos a ciegas: 10 intentos fallidos por IP cada 10 minutos.
_pair_failures: dict[str, list[float]] = {}
_pair_lock = threading.Lock()


def _client_ip(request: Request) -> str:
    return request.headers.get("cf-connecting-ip") or (request.client.host if request.client else "?")


@app.post("/v1/pair")
async def pair(request: Request) -> JSONResponse:
    try:
        body = await request.json()
    except (json.JSONDecodeError, ValueError):
        body = None
    if not isinstance(body, dict) or not isinstance(body.get("code"), str):
        return JSONResponse({"detail": "Falta el código"}, status_code=400)

    ip = _client_ip(request)
    now = time.monotonic()
    with _pair_lock:
        recent = [t for t in _pair_failures.get(ip, []) if now - t < 600]
        _pair_failures[ip] = recent
        if len(recent) >= 10:
            return JSONResponse({"detail": "Demasiados intentos; espera unos minutos"}, status_code=429)

    name = str(body.get("name") or "Dispositivo")[:80]
    with transaction() as conn:
        result = accounts.redeem_pairing_code(conn, body["code"], name)
    if result is None:
        with _pair_lock:
            _pair_failures.setdefault(ip, []).append(now)
        return JSONResponse({"detail": "Código no válido o caducado"}, status_code=400)
    account, token = result
    return JSONResponse({"account": account.username, "token": token})
