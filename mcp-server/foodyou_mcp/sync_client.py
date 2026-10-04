"""Cliente del servidor de sincronización (docs/sync/protocol.md) para el MCP.

El MCP es un dispositivo más de la cuenta: lee el diario tirando del servidor y escribe
mandándole cambios, igual que el móvil o el reloj. Lo que escribe Claude llega así a todos
los dispositivos, y el servidor sigue siendo el que decide quién tiene acceso: el token MCP
se le reenvía tal cual en cada petición.

Guarda en memoria una copia del diario por cuenta y solo pide lo nuevo (cursor).
"""

from __future__ import annotations

import asyncio
import hashlib
import time
from dataclasses import dataclass, field
from typing import Any, Optional

import httpx

DEVICE = "mcp"


class AccessDenied(Exception):
    """El token no vale o la cuenta no tiene acceso MCP."""


@dataclass
class Document:
    kind: str
    id: str
    seq: int
    deleted: bool
    fields: dict[str, dict[str, Any]]

    def value(self, name: str, default=None):
        field_ = self.fields.get(name)
        return field_["value"] if field_ else default

    def values(self) -> dict[str, Any]:
        return {k: v["value"] for k, v in self.fields.items() if k != "_deleted"}

    def clock(self, name: str) -> int:
        field_ = self.fields.get(name)
        return int(field_["clock"]) if field_ else 0


@dataclass
class AccountCache:
    account: str = ""
    cursor: int = 0
    documents: dict[tuple[str, str], Document] = field(default_factory=dict)
    lock: asyncio.Lock = field(default_factory=asyncio.Lock)
    # La cuenta de foods asignada en el panel; se vuelve a mirar cada minuto.
    food_account: Optional[str] = None
    status_until: float = 0


def now_ms() -> int:
    return int(time.time() * 1000)


class SyncClient:
    def __init__(self, base_url: str, transport: Optional[httpx.AsyncBaseTransport] = None):
        self._base = base_url.rstrip("/")
        self._transport = transport
        self._caches: dict[str, AccountCache] = {}

    def _client(self, token: str) -> httpx.AsyncClient:
        return httpx.AsyncClient(
            base_url=self._base,
            headers={"Authorization": f"Bearer {token}", "X-Device-Id": DEVICE, "User-Agent": "FoodYou-MCP"},
            timeout=30,
            transport=self._transport,
        )

    def _cache(self, token: str) -> AccountCache:
        key = hashlib.sha256(token.encode()).hexdigest()
        return self._caches.setdefault(key, AccountCache())

    @staticmethod
    def _check(response: httpx.Response) -> dict:
        if response.status_code in (401, 403):
            raise AccessDenied(
                "El token no es válido o la cuenta no tiene el acceso MCP activado "
                "(se activa en el panel de sincronización)."
            )
        response.raise_for_status()
        return response.json()

    async def account(self, token: str) -> str:
        cache = self._cache(token)
        if not cache.account:
            async with self._client(token) as client:
                cache.account = self._check(await client.get("/v1/status"))["account"]
        return cache.account

    async def food_account(self, token: str) -> Optional[str]:
        """La cuenta de foods en cuyo nombre buscar (la asigna el panel de sincronización)."""
        cache = self._cache(token)
        if cache.status_until < time.monotonic():
            async with self._client(token) as client:
                status = self._check(await client.get("/v1/status"))
            cache.account = status["account"]
            cache.food_account = status.get("foodAccount") or None
            cache.status_until = time.monotonic() + 60
        return cache.food_account

    async def documents(self, token: str) -> dict[tuple[str, str], Document]:
        """El diario de la cuenta, al día: trae solo lo que cambió desde la última vez."""
        cache = self._cache(token)
        async with cache.lock:
            await self._exchange(token, cache, [])
            return dict(cache.documents)

    async def push(self, token: str, changes: list[dict]) -> dict[tuple[str, str], Document]:
        """Manda cambios ya con sus relojes y devuelve el diario tal como queda."""
        cache = self._cache(token)
        async with cache.lock:
            await self._exchange(token, cache, changes)
            return dict(cache.documents)

    async def _exchange(self, token: str, cache: AccountCache, changes: list[dict]) -> None:
        async with self._client(token) as client:
            pending = changes
            while True:
                body = self._check(
                    await client.post("/v1/sync", json={"cursor": cache.cursor, "changes": pending})
                )
                pending = []
                for d in body["documents"]:
                    cache.documents[(d["kind"], d["id"])] = Document(
                        d["kind"], d["id"], d["seq"], d.get("deleted", False), d["fields"]
                    )
                cache.cursor = body["cursor"]
                if not body.get("more"):
                    break


def change(kind: str, doc_id: str, values: dict[str, Any], current: Optional[Document] = None) -> dict:
    """Un cambio con relojes que nunca quedan por detrás de lo que el servidor ya tiene."""
    base = now_ms()
    fields = {}
    for name, value in values.items():
        clock = max(base, (current.clock(name) + 1) if current else 0)
        fields[name] = {"value": value, "clock": clock}
    return {"kind": kind, "id": doc_id, "fields": fields}
