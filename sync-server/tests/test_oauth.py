"""El conector de claude.ai: descubrimiento, registro, autorizar con la cuenta, PKCE,
renovar y revocar."""

from __future__ import annotations

import base64
import hashlib
import secrets
from urllib.parse import parse_qs, urlsplit

from app import accounts
from app.db import transaction

CALLBACK = "https://claude.ai/api/mcp/auth_callback"


def _enable_mcp(name="ana"):
    with transaction() as conn:
        conn.execute("UPDATE accounts SET mcp_enabled = 1 WHERE username = ?", (name,))


def _register(client, **extra):
    r = client.post("/oauth/register", json={"client_name": "Claude", "redirect_uris": [CALLBACK], **extra})
    assert r.status_code == 201, r.text
    return r.json()


def _pkce():
    verifier = secrets.token_urlsafe(48)
    challenge = base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).rstrip(b"=").decode()
    return verifier, challenge


def _params(client_id, challenge):
    return {
        "response_type": "code",
        "client_id": client_id,
        "redirect_uri": CALLBACK,
        "state": "xyz",
        "code_challenge": challenge,
        "code_challenge_method": "S256",
        "scope": "diary",
        "resource": "https://sync.alexeido.com/mcp",
    }


def _authorize(client, params, user="ana", password="clave-ana", decision="allow"):
    data = {k: v for k, v in params.items() if k != "response_type"}
    return client.post(
        "/oauth/authorize",
        data={**data, "username": user, "password": password, "decision": decision},
        follow_redirects=False,
    )


def _code_flow(client):
    reg = _register(client)
    verifier, challenge = _pkce()
    params = _params(reg["client_id"], challenge)
    page = client.get("/oauth/authorize", params=params)
    assert page.status_code == 200 and "Permitir" in page.text
    r = _authorize(client, params)
    assert r.status_code == 303, r.text
    query = parse_qs(urlsplit(r.headers["location"]).query)
    assert r.headers["location"].startswith(CALLBACK) and query["state"] == ["xyz"]
    return reg, verifier, query["code"][0]


def _exchange(client, reg, verifier, code):
    return client.post(
        "/oauth/token",
        data={
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": CALLBACK,
            "client_id": reg["client_id"],
            "code_verifier": verifier,
        },
    )


def _refresh(client, reg, refresh):
    return client.post(
        "/oauth/token",
        data={"grant_type": "refresh_token", "refresh_token": refresh, "client_id": reg["client_id"]},
    )


def _status(client, token):
    return client.get("/v1/status", headers={"authorization": f"Bearer {token}"})


def test_discovery_points_to_this_server(client):
    pr = client.get("/.well-known/oauth-protected-resource/mcp").json()
    assert pr["resource"] == "https://sync.alexeido.com/mcp"
    assert pr["authorization_servers"] == ["https://sync.alexeido.com"]
    meta = client.get("/.well-known/oauth-authorization-server").json()
    assert meta["issuer"] == "https://sync.alexeido.com"
    assert meta["code_challenge_methods_supported"] == ["S256"]
    assert meta["registration_endpoint"].endswith("/oauth/register")


def test_full_flow_gives_a_working_mcp_token_that_refreshes(client):
    _enable_mcp()
    reg, verifier, code = _code_flow(client)
    r = _exchange(client, reg, verifier, code)
    assert r.status_code == 200, r.text
    tokens = r.json()
    assert tokens["token_type"] == "Bearer" and tokens["expires_in"] > 0
    status = _status(client, tokens["access_token"])
    assert status.status_code == 200 and status.json()["access"] == "mcp"

    # El código vale una vez.
    assert _exchange(client, reg, verifier, code).json()["error"] == "invalid_grant"

    renewed = _refresh(client, reg, tokens["refresh_token"])
    assert renewed.status_code == 200, renewed.text
    new = renewed.json()
    # El token viejo y el refresco viejo dejan de valer.
    assert _status(client, tokens["access_token"]).status_code == 401
    assert _status(client, new["access_token"]).status_code == 200
    assert _refresh(client, reg, tokens["refresh_token"]).json()["error"] == "invalid_grant"

    # Una sola entrada en el panel por conector, y revocarla corta todo.
    with transaction() as conn:
        ana = accounts.get_by_name(conn, "ana")
        rows = accounts.list_tokens(conn, ana.id)
        assert [row["name"] for row in rows] == ["Claude (conector)"]
        accounts.revoke_token(conn, ana.id, rows[0]["id"])
    assert _status(client, new["access_token"]).status_code == 401
    assert _refresh(client, reg, new["refresh_token"]).json()["error"] == "invalid_grant"


def test_wrong_pkce_is_refused(client):
    _enable_mcp()
    reg, _, code = _code_flow(client)
    assert _exchange(client, reg, "otro-verifier", code).json()["error"] == "invalid_grant"


def test_accounts_without_mcp_or_wrong_password_cannot_authorize(client):
    reg = _register(client)
    _, challenge = _pkce()
    params = _params(reg["client_id"], challenge)
    r = _authorize(client, params)  # ana aún no tiene MCP
    assert r.status_code == 403 and "acceso MCP" in r.text
    _enable_mcp()
    r = _authorize(client, params, password="mala")
    assert r.status_code == 401 and "incorrectos" in r.text
    r = _authorize(client, params, decision="deny")
    assert r.status_code == 303 and "error=access_denied" in r.headers["location"]


def test_disabling_mcp_stops_the_connector(client):
    _enable_mcp()
    reg, verifier, code = _code_flow(client)
    tokens = _exchange(client, reg, verifier, code).json()
    with transaction() as conn:
        conn.execute("UPDATE accounts SET mcp_enabled = 0 WHERE username = 'ana'")
    assert _status(client, tokens["access_token"]).status_code == 401
    assert _refresh(client, reg, tokens["refresh_token"]).json()["error"] == "invalid_grant"


def test_bad_registrations_and_redirects_are_refused(client):
    assert client.post("/oauth/register", json={"redirect_uris": ["http://evil.example/cb"]}).status_code == 400
    assert client.post("/oauth/register", json={}).status_code == 400
    reg = _register(client)
    _, challenge = _pkce()
    params = {**_params(reg["client_id"], challenge), "redirect_uri": "https://evil.example/cb"}
    r = client.get("/oauth/authorize", params=params, follow_redirects=False)
    assert r.status_code == 400 and "no coincide" in r.text
    r = client.get(
        "/oauth/authorize",
        params={**_params(reg["client_id"], challenge), "code_challenge": ""},
        follow_redirects=False,
    )
    assert r.status_code == 303 and "invalid_request" in r.headers["location"]


def test_confidential_clients_need_their_secret(client):
    _enable_mcp()
    reg = _register(client, token_endpoint_auth_method="client_secret_post")
    assert reg["client_secret"]
    verifier, challenge = _pkce()
    r = _authorize(client, _params(reg["client_id"], challenge))
    code = parse_qs(urlsplit(r.headers["location"]).query)["code"][0]
    assert _exchange(client, reg, verifier, code).status_code == 401
    r = client.post(
        "/oauth/token",
        data={
            "grant_type": "authorization_code",
            "code": code,
            "redirect_uri": CALLBACK,
            "client_id": reg["client_id"],
            "client_secret": reg["client_secret"],
            "code_verifier": verifier,
        },
    )
    # El intento sin secreto no llegó a gastar el código.
    assert r.status_code == 200, r.text
