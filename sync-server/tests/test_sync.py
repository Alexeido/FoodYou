"""El protocolo de docs/sync/protocol.md, comprobado contra el servidor de verdad."""

from __future__ import annotations

from conftest import change, sync


def _doc(response, doc_id):
    return next(d for d in response["documents"] if d["id"] == doc_id)


def test_a_new_document_comes_back_merged(client):
    r = sync(client, [change("meal", "m1", 1000, name="Desayuno", rank=0)])
    assert r["cursor"] == 1 and r["more"] is False
    doc = _doc(r, "m1")
    assert doc["kind"] == "meal" and doc["seq"] == 1 and doc["deleted"] is False
    assert doc["fields"]["name"] == {"value": "Desayuno", "clock": 1000, "device": "movil-a"}


def test_the_newest_change_wins_field_by_field(client):
    sync(client, [change("food_entry", "e1", 1000, isEaten=0, quantity=100)])
    # Desde el reloj se marca como comido (más nuevo) y a la vez el móvil cambia los
    # gramos con un reloj más nuevo todavía: los dos cambios sobreviven.
    sync(client, [change("food_entry", "e1", 2000, isEaten=1)], device="reloj")
    r = sync(client, [change("food_entry", "e1", 3000, quantity=150)], device="movil-a")
    fields = _doc(r, "e1")["fields"]
    assert fields["isEaten"]["value"] == 1 and fields["isEaten"]["device"] == "reloj"
    assert fields["quantity"]["value"] == 150


def test_an_older_change_arriving_late_loses(client):
    sync(client, [change("food_entry", "e1", 5000, quantity=150)])
    # Hecho sin cobertura hace rato: llega después, pero es más antiguo.
    r = sync(client, [change("food_entry", "e1", 4000, quantity=90)], device="reloj")
    assert _doc(r, "e1")["fields"]["quantity"]["value"] == 150


def test_ties_are_broken_the_same_way_everywhere(client):
    sync(client, [change("meal", "m1", 1000, name="A")], device="aaa")
    r = sync(client, [change("meal", "m1", 1000, name="B")], device="zzz")
    assert _doc(r, "m1")["fields"]["name"]["value"] == "B"
    r = sync(client, [change("meal", "m1", 1000, name="C")], device="bbb")
    assert _doc(r, "m1")["fields"]["name"]["value"] == "B"


def test_deleting_is_a_field_so_old_edits_do_not_resurrect(client):
    sync(client, [change("food_entry", "e1", 1000, quantity=100)])
    r = sync(client, [change("food_entry", "e1", 3000, _deleted=True)])
    assert _doc(r, "e1")["deleted"] is True
    # Una edición más vieja que se quedó en otro móvil no lo resucita...
    r = sync(client, [change("food_entry", "e1", 2000, quantity=120)], device="reloj")
    assert _doc(r, "e1")["deleted"] is True
    # ...pero deshacer el borrado, más nuevo, sí.
    r = sync(client, [change("food_entry", "e1", 4000, _deleted=False)])
    assert _doc(r, "e1")["deleted"] is False


def test_the_cursor_only_brings_what_is_new(client):
    first = sync(client, [change("meal", "m1", 1000, name="Desayuno")])
    sync(client, [change("meal", "m2", 1000, name="Comida")], device="otro")
    r = sync(client, cursor=first["cursor"])
    assert [d["id"] for d in r["documents"]] == ["m2"]
    # Nada nuevo: el mismo cursor y ningún documento.
    again = sync(client, cursor=r["cursor"])
    assert again["documents"] == [] and again["cursor"] == r["cursor"]


def test_an_unchanged_document_does_not_get_a_new_seq(client):
    r = sync(client, [change("meal", "m1", 1000, name="Desayuno")])
    again = sync(client, [change("meal", "m1", 1000, name="Desayuno")], cursor=r["cursor"])
    assert again["documents"] == []


def test_large_pulls_are_paged(client, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "max_documents_per_response", 3)
    sync(client, [change("meal", f"m{i}", 1000, rank=i) for i in range(7)])
    cursor, seen, pages = 0, [], 0
    while True:
        r = sync(client, cursor=cursor)
        seen += [d["id"] for d in r["documents"]]
        cursor, pages = r["cursor"], pages + 1
        if not r["more"]:
            break
    assert seen == [f"m{i}" for i in range(7)] and pages == 3


def test_accounts_never_see_each_other(client):
    sync(client, [change("meal", "m1", 1000, name="De Ana")], user="ana")
    r = sync(client, user="bea")
    assert r["documents"] == []
    sync(client, [change("meal", "m1", 1000, name="De Bea")], user="bea")
    assert _doc(sync(client, user="ana"), "m1")["fields"]["name"]["value"] == "De Ana"


def test_a_cursor_from_somewhere_else_starts_over(client):
    sync(client, [change("meal", "m1", 1000, name="Desayuno")])
    r = sync(client, cursor=999)
    assert [d["id"] for d in r["documents"]] == ["m1"]


def test_a_clock_far_in_the_future_is_capped(client):
    from app.store import now_ms

    r = sync(client, [change("meal", "m1", now_ms() + 10**12, name="Del año 2999")])
    assert _doc(r, "m1")["fields"]["name"]["clock"] <= now_ms() + 5 * 60 * 1000


def test_bad_requests_change_nothing(client):
    good = change("meal", "m1", 1000, name="Desayuno")
    bad = {"kind": "Meal!", "id": "x", "fields": {"a": {"value": 1, "clock": 1}}}
    r = client.post(
        "/v1/sync", json={"cursor": 0, "changes": [good, bad]}, auth=("ana", "clave-ana")
    )
    assert r.status_code == 400
    assert sync(client)["documents"] == []

    for body in (
        {"cursor": -1},
        {"cursor": 0, "changes": [{"kind": "meal", "id": "m", "fields": {}}]},
        {"cursor": 0, "changes": [{"kind": "meal", "id": "m", "fields": {"a": {"value": 1}}}]},
        {"cursor": 0, "changes": [change("meal", "m", 1, _deleted="si")]},
    ):
        assert client.post("/v1/sync", json=body, auth=("ana", "clave-ana")).status_code == 400


def test_too_many_changes_is_rejected(client, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "max_changes_per_request", 2)
    r = client.post(
        "/v1/sync",
        json={"cursor": 0, "changes": [change("meal", f"m{i}", 1, rank=i) for i in range(3)]},
        auth=("ana", "clave-ana"),
    )
    assert r.status_code == 413


def test_authentication(client):
    assert client.post("/v1/sync", json={"cursor": 0}).status_code == 401
    assert client.post("/v1/sync", json={"cursor": 0}, auth=("ana", "mal")).status_code == 401
    assert client.get("/health").json() == {"status": "ok"}

    from app import accounts
    from app.db import transaction

    with transaction() as conn:
        accounts.set_active(conn, "bea", False)
    assert client.get("/v1/status", auth=("bea", "clave-bea")).status_code == 403


def test_status(client):
    sync(client, [change("meal", "m1", 1000, name="Desayuno")])
    s = client.get("/v1/status", auth=("ana", "clave-ana")).json()
    assert s["account"] == "ana" and s["cursor"] == 1 and s["documents"] == 1
    assert s["serverTime"] > 0


def test_any_json_value_round_trips(client):
    food = {"product": {"name": "Avena", "energy": 372.0, "isLiquid": 0, "note": None}}
    r = sync(client, [change("food_entry", "e1", 1000, food=food, meal="m1")])
    assert _doc(r, "e1")["fields"]["food"]["value"] == food
