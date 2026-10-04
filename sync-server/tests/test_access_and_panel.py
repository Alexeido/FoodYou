"""Tokens (reloj y MCP), emparejar con código, avisos en vivo y el panel."""

from __future__ import annotations

import asyncio

from conftest import change, sync

ORIGIN = {"origin": "http://testserver"}


def _admin_login(client, password="admin"):
    r = client.post(
        "/admin/login",
        data={"username": "admin", "password": password},
        headers=ORIGIN,
        follow_redirects=False,
    )
    assert r.status_code == 303, r.text


def _account_id(name):
    from app import accounts
    from app.db import connect

    conn = connect()
    try:
        return accounts.get_by_name(conn, name).id
    finally:
        conn.close()


# --- Emparejar un dispositivo -------------------------------------------------------


def test_a_watch_pairs_with_a_code_and_syncs_with_its_token(client):
    r = client.post("/v1/pairing-codes", auth=("ana", "clave-ana"))
    assert r.status_code == 200
    code = r.json()["code"]
    assert len(code) == 6 and code.isdigit()

    paired = client.post("/v1/pair", json={"code": code, "name": "Reloj"})
    assert paired.status_code == 200
    token = paired.json()["token"]
    assert paired.json()["account"] == "ana"

    headers = {"authorization": f"Bearer {token}", "x-device-id": "reloj"}
    r = client.post("/v1/sync", json={"cursor": 0, "changes": [change("meal", "m1", 1, name="Cena")]}, headers=headers)
    assert r.status_code == 200
    # Lo que escribe el reloj lo ve el móvil de la misma cuenta.
    assert [d["id"] for d in sync(client)["documents"]] == ["m1"]

    # Un código vale una sola vez.
    assert client.post("/v1/pair", json={"code": code}).status_code == 400


def test_wrong_codes_are_rate_limited(client):
    for _ in range(10):
        assert client.post("/v1/pair", json={"code": "000000"}).status_code in (400, 429)
    assert client.post("/v1/pair", json={"code": "000000"}).status_code == 429


def test_a_revoked_device_loses_access(client):
    code = client.post("/v1/pairing-codes", auth=("ana", "clave-ana")).json()["code"]
    token = client.post("/v1/pair", json={"code": code}).json()["token"]
    headers = {"authorization": f"Bearer {token}"}
    assert client.get("/v1/status", headers=headers).status_code == 200

    from app import accounts
    from app.db import transaction

    with transaction() as conn:
        for t in accounts.list_tokens(conn, _account_id("ana")):
            accounts.revoke_token(conn, _account_id("ana"), t["id"])
    assert client.get("/v1/status", headers=headers).status_code == 401


# --- MCP ----------------------------------------------------------------------------


def test_mcp_tokens_only_work_while_the_account_has_mcp(client):
    _admin_login(client)
    ana = _account_id("ana")

    # Sin el acceso activado, ni siquiera se puede crear un token.
    r = client.post(f"/admin/accounts/{ana}/mcp-token", data={"name": "Claude"}, headers=ORIGIN)
    assert r.status_code == 400

    client.post(
        f"/admin/accounts/{ana}",
        data={"is_active": "on", "mcp_enabled": "on", "note": ""},
        headers=ORIGIN,
        follow_redirects=False,
    )
    page = client.post(f"/admin/accounts/{ana}/mcp-token", data={"name": "Claude"}, headers=ORIGIN)
    assert page.status_code == 200
    token = page.text.split("Authorization: Bearer ")[1].split('"')[0]
    assert token.startswith("fy_mcp_")
    assert "claude mcp add --transport http" in page.text

    headers = {"authorization": f"Bearer {token}"}
    r = client.post("/v1/sync", json={"cursor": 0, "changes": [change("meal", "m1", 1, name="Desde Claude")]}, headers=headers)
    assert r.status_code == 200
    assert r.json()["documents"][0]["fields"]["name"]["device"] == "mcp"
    # Un token MCP no puede emparejar dispositivos.
    assert client.post("/v1/pairing-codes", headers=headers).status_code == 403

    # Quitar el acceso MCP corta el token al momento, sin tener que retirarlo.
    client.post(f"/admin/accounts/{ana}", data={"is_active": "on", "note": ""}, headers=ORIGIN)
    assert client.get("/v1/status", headers=headers).status_code == 401


def test_the_panel_links_a_food_account_that_the_mcp_reads(client):
    _admin_login(client)
    ana = _account_id("ana")
    r = client.post(
        f"/admin/accounts/{ana}",
        data={"is_active": "on", "mcp_enabled": "on", "note": "", "food_account": " ana_foods "},
        headers=ORIGIN,
        follow_redirects=False,
    )
    assert r.status_code == 303
    assert "Foods: ana_foods" in client.get(f"/admin/accounts/{ana}").text
    page = client.post(f"/admin/accounts/{ana}/mcp-token", data={"name": "Claude"}, headers=ORIGIN)
    token = page.text.split("Authorization: Bearer ")[1].split('"')[0]
    status = client.get("/v1/status", headers={"authorization": f"Bearer {token}"}).json()
    assert status["foodAccount"] == "ana_foods" and status["access"] == "mcp"

    r = client.post(
        f"/admin/accounts/{ana}",
        data={"is_active": "on", "mcp_enabled": "on", "note": "", "food_account": "con espacio"},
        headers=ORIGIN,
    )
    assert r.status_code == 400


# --- Avisos en vivo -----------------------------------------------------------------


def test_the_notifier_wakes_the_right_account():
    from app.events import Notifier

    async def scenario():
        n = Notifier()
        seen = n.version(1)
        waiter = asyncio.create_task(n.wait(1, seen, timeout=2))
        other = asyncio.create_task(n.wait(2, n.version(2), timeout=0.2))
        await asyncio.sleep(0.05)
        await n.notify(1)
        return await waiter, await other

    woke, other_woke = asyncio.run(scenario())
    assert woke is True and other_woke is False


def test_a_sync_with_changes_notifies_the_account(client, monkeypatch):
    from app import main

    calls = []

    async def fake_notify(account_id):
        calls.append(account_id)

    monkeypatch.setattr(main.notifier, "notify", fake_notify)
    sync(client, [change("meal", "m1", 1, name="Desayuno")])
    sync(client)  # sin cambios: nadie a quien avisar
    assert calls == [_account_id("ana")]


def test_events_needs_authentication(client):
    assert client.get("/v1/events").status_code == 401


# --- Panel --------------------------------------------------------------------------


def test_the_panel_lists_who_syncs_without_showing_diaries(client):
    sync(client, [change("food_entry", "e1", 1, food={"product": {"name": "Secreto"}})], device="movil-ana")
    _admin_login(client)

    page = client.get("/admin").text
    assert "ana" in page and "bea" in page
    assert "Secreto" not in page  # nunca el contenido

    detail = client.get(f"/admin/accounts/{_account_id('ana')}").text
    assert "movil-ana" in detail
    assert "Entradas del diario" in detail
    assert "Secreto" not in detail


def test_the_panel_creates_accounts_and_changes_passwords(client):
    _admin_login(client)
    r = client.post(
        "/admin/accounts/new",
        data={"username": "carla", "password": "clave-carla", "note": "Amiga"},
        headers=ORIGIN,
        follow_redirects=False,
    )
    assert r.status_code == 303
    assert client.get("/v1/status", auth=("carla", "clave-carla")).status_code == 200

    carla = _account_id("carla")
    client.post(
        f"/admin/accounts/{carla}",
        data={"is_active": "on", "password": "otra-clave", "note": ""},
        headers=ORIGIN,
    )
    assert client.get("/v1/status", auth=("carla", "clave-carla")).status_code == 401
    assert client.get("/v1/status", auth=("carla", "otra-clave")).status_code == 200

    client.post(f"/admin/accounts/{carla}", data={"note": ""}, headers=ORIGIN)  # desactivar
    assert client.get("/v1/status", auth=("carla", "otra-clave")).status_code == 403


def test_the_panel_is_protected(client):
    assert client.get("/admin", follow_redirects=False).status_code == 303
    # Una cuenta normal no entra en el panel.
    r = client.post("/admin/login", data={"username": "ana", "password": "clave-ana"}, headers=ORIGIN)
    assert r.status_code == 401
    _admin_login(client)
    assert "contraseña inicial" in client.get("/admin").text
    # Peticiones desde otra web, rechazadas.
    r = client.post("/admin/accounts/new", data={"username": "x", "password": "y"}, headers={"origin": "https://mala.example"})
    assert r.status_code == 403


def test_the_admin_cannot_lock_themselves_out(client):
    _admin_login(client)
    me = _account_id("admin")
    r = client.post(f"/admin/accounts/{me}", data={"is_active": "on", "note": ""}, headers=ORIGIN)
    assert r.status_code == 400
    assert client.post(f"/admin/accounts/{me}/delete", headers=ORIGIN).status_code == 400
