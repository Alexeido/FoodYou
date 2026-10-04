"""El MCP por HTTP, como lo usa Claude Code: listar herramientas y llamarlas con el token."""

from __future__ import annotations

import json

from conftest import FakeFoods, meal_change
from mcp.server.transport_security import TransportSecuritySettings
from starlette.testclient import TestClient

from foodyou_mcp.server import asgi, build
from foodyou_mcp.sync_client import SyncClient

HEADERS = {"accept": "application/json, text/event-stream", "content-type": "application/json"}


def _client(world):
    server, _ = build(
        world["config"],
        sync=SyncClient("http://sync", transport=world["transport"]),
        food=FakeFoods(),
    )
    app = asgi(
        server,
        world["config"],
        sync_transport=world["transport"],
        transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
    )
    return TestClient(app)


def _rpc(client, method, params=None, token=None, rid=1):
    headers = dict(HEADERS)
    if token:
        headers["authorization"] = f"Bearer {token}"
    r = client.post(
        "/mcp",
        content=json.dumps({"jsonrpc": "2.0", "id": rid, "method": method, "params": params or {}}),
        headers=headers,
    )
    assert r.status_code == 200, r.text
    return r.json()


def _init(client, token):
    return _rpc(
        client,
        "initialize",
        {"protocolVersion": "2025-06-18", "capabilities": {}, "clientInfo": {"name": "test", "version": "1"}},
        token,
    )


def test_claude_sees_the_tools(world):
    with _client(world) as client:
        info = _init(client, world["token"])
        assert info["result"]["serverInfo"]["name"] == "Food You"
        assert "search_food" in info["result"]["instructions"]
        tools = {t["name"] for t in _rpc(client, "tools/list", token=world["token"], rid=2)["result"]["tools"]}
        assert {
            "get_day", "get_goals", "get_totals", "top_foods", "top_brands", "nutrient_sources", "meal_times",
            "find_in_diary", "search_food", "add_food", "add_foods", "add_quick_entry", "update_entry", "set_eaten",
            "delete_entries", "undo_last", "redo", "plan_start", "plan_add", "plan_remove", "plan_show",
            "plan_commit", "plan_discard", "get_recipes", "get_recipe", "create_recipe", "delete_recipe",
            "get_memory", "remember", "forget",
        } <= tools


def test_a_tool_call_with_the_token_reads_the_diary(world):
    import asyncio

    asyncio.run(world["phone"].sync([meal_change("m1", "Desayuno", 0)]))
    with _client(world) as client:
        _init(client, world["token"])
        result = _rpc(client, "tools/call", {"name": "get_meals", "arguments": {}}, world["token"], rid=3)["result"]
        assert result.get("isError") is not True
        assert "Desayuno" in json.dumps(result)


def _post(client, token=None):
    headers = dict(HEADERS)
    if token:
        headers["authorization"] = f"Bearer {token}"
    body = {"jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {}}
    return client.post("/mcp", content=json.dumps(body), headers=headers)


def test_without_a_valid_mcp_token_it_asks_to_log_in(world):
    """El 401 con resource_metadata es lo que lleva a claude.ai a la pantalla de OAuth."""
    with _client(world) as client:
        for token in (None, "fy_mcp_inventado", world["bea_token"], world["device_token"]):
            r = _post(client, token)
            assert r.status_code == 401, (token, r.text)
            challenge = r.headers["www-authenticate"]
            assert 'resource_metadata="https://sync.alexeido.com/.well-known/oauth-protected-resource/mcp"' in challenge
        assert _post(client, world["token"]).status_code == 200
        assert client.get("/health").status_code == 200


def test_a_token_from_the_oauth_login_works_in_the_mcp(world):
    """El camino de claude.ai entero: registrarse, iniciar sesión, cambiar el código y usarlo."""
    import base64
    import hashlib
    import secrets
    from urllib.parse import parse_qs, urlsplit

    from starlette.testclient import TestClient as Sync

    from app.main import app as sync_app

    callback = "https://claude.ai/api/mcp/auth_callback"
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    with Sync(sync_app) as auth:
        client_id = auth.post("/oauth/register", json={"client_name": "Claude", "redirect_uris": [callback]}).json()["client_id"]
        r = auth.post(
            "/oauth/authorize",
            data={
                "client_id": client_id, "redirect_uri": callback, "state": "s", "code_challenge": challenge,
                "code_challenge_method": "S256", "scope": "diary", "resource": "https://sync.alexeido.com/mcp",
                "username": "ana", "password": "clave-ana", "decision": "allow",
            },
            follow_redirects=False,
        )
        code = parse_qs(urlsplit(r.headers["location"]).query)["code"][0]
        token = auth.post(
            "/oauth/token",
            data={"grant_type": "authorization_code", "code": code, "redirect_uri": callback,
                  "client_id": client_id, "code_verifier": verifier},
        ).json()["access_token"]

    with _client(world) as client:
        _init(client, token)
        result = _rpc(client, "tools/call", {"name": "get_meals", "arguments": {}}, token, rid=2)["result"]
        assert result.get("isError") is not True
