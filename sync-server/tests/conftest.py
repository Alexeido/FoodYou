from __future__ import annotations

import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))


@pytest.fixture()
def client(tmp_path, monkeypatch):
    monkeypatch.setenv("DB_PATH", str(tmp_path / "sync.db"))
    from app.config import Settings, get_settings

    monkeypatch.setitem(Settings.model_config, "env_file", None)
    get_settings.cache_clear()

    from fastapi.testclient import TestClient

    from app import accounts
    from app.db import init_schema, transaction
    from app.main import app

    accounts.forget_verified()
    # Los frenos a la fuerza bruta viven en memoria: cada test empieza de cero.
    from app import main as main_module
    from app.admin import router as admin_router

    main_module._pair_failures.clear()
    admin_router._failures.clear()
    from app import oauth

    oauth._failures.clear()
    oauth._registrations.clear()
    init_schema()
    with transaction() as conn:
        accounts.create(conn, "ana", "clave-ana")
        accounts.create(conn, "bea", "clave-bea")
    with TestClient(app) as c:
        yield c
    get_settings.cache_clear()


def sync(client, changes=(), cursor=0, user="ana", device="movil-a"):
    r = client.post(
        "/v1/sync",
        json={"cursor": cursor, "changes": list(changes)},
        auth=(user, f"clave-{user}"),
        headers={"x-device-id": device},
    )
    assert r.status_code == 200, r.text
    return r.json()


def change(kind, doc_id, clock, **fields):
    return {
        "kind": kind,
        "id": doc_id,
        "fields": {name: {"value": value, "clock": clock} for name, value in fields.items()},
    }
