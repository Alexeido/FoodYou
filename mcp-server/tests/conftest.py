"""El MCP contra el servidor de sincronización de verdad (sync-server/, en proceso) y una
base de datos de alimentos de mentira."""

from __future__ import annotations

import sys
from pathlib import Path

import httpx
import pytest

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))
sys.path.insert(0, str(ROOT.parent / "sync-server"))

PRODUCTS = [
    {
        "name": "Copos de avena",
        "brand": "Hacendado",
        "servingWeight": 40.0,
        "isLiquid": False,
        "categories": ["en:breakfast-cereals"],
        "url": "https://example.test/avena",
        "nutritionFacts": {"energy": 372.0, "proteins": 13.0, "carbohydrates": 59.0, "fats": 7.0, "cholesterol": 0.001},
    },
    {
        "name": "Leche semidesnatada",
        "brand": None,
        "isLiquid": True,
        "nutritionFacts": {"energy": 46.0, "proteins": 3.3, "carbohydrates": 4.7, "fats": 1.6, "calcium": 0.12},
    },
]


class FakeFoods:
    configured = True

    def __init__(self):
        self.on_behalf = []

    async def search(self, query, limit, on_behalf=None):
        self.on_behalf.append(on_behalf)
        return [p for p in PRODUCTS if query.lower() in p["name"].lower()][:limit]


@pytest.fixture()
def world(tmp_path, monkeypatch):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "sync.db"))
    from app.config import Settings, get_settings

    monkeypatch.setitem(Settings.model_config, "env_file", None)
    get_settings.cache_clear()

    from app import accounts
    from app.db import init_schema, transaction
    from app.main import app as sync_app

    accounts.forget_verified()
    init_schema()
    with transaction() as conn:
        ana = accounts.create(conn, "ana", "clave-ana")
        conn.execute("UPDATE accounts SET mcp_enabled = 1 WHERE id = ?", (ana,))
        token = accounts.create_token(conn, ana, accounts.MCP, "Claude")
        bea = accounts.create(conn, "bea", "clave-bea")
        bea_token = accounts.create_token(conn, bea, accounts.MCP, "Claude")  # bea no tiene MCP
        # Un token de dispositivo (el reloj) de ana: sincroniza, pero no da acceso MCP.
        device_token = accounts.create_token(conn, ana, accounts.DEVICE, "Reloj")

    transport = httpx.ASGITransport(app=sync_app)

    from foodyou_mcp.config import Config
    from foodyou_mcp.diary import Diary
    from foodyou_mcp.journal import Journal
    from foodyou_mcp.sync_client import SyncClient

    sync = SyncClient("http://sync", transport=transport)
    diary = Diary(sync, FakeFoods(), Journal(tmp_path / "mcp.db"))

    class Phone:
        """El móvil de ana, que habla con el mismo servidor con su contraseña."""

        async def sync(self, changes=(), cursor=0):
            async with httpx.AsyncClient(base_url="http://sync", transport=transport) as c:
                r = await c.post(
                    "/v1/sync",
                    json={"cursor": cursor, "changes": list(changes)},
                    auth=("ana", "clave-ana"),
                    headers={"x-device-id": "movil"},
                )
                r.raise_for_status()
                return {(d["kind"], d["id"]): d for d in r.json()["documents"]}

    from foodyou_mcp.analysis import Analysis
    from foodyou_mcp.plan import Plans

    yield {
        "diary": diary,
        "analysis": Analysis(diary),
        "plans": Plans(diary),
        "token": token,
        "bea_token": bea_token,
        "device_token": device_token,
        "phone": Phone(),
        "transport": transport,
        "config": Config(sync_url="http://sync", data_dir=tmp_path, open_food_facts=False),
    }
    get_settings.cache_clear()


def meal_change(meal_id, name, rank):
    fields = {
        "name": name, "fromHour": 8, "fromMinute": 0, "toHour": 10, "toMinute": 0, "rank": rank, "icon": None,
    }
    return {"kind": "meal", "id": meal_id, "fields": {k: {"value": v, "clock": 1} for k, v in fields.items()}}
