"""Lo que puede hacer Claude con el diario, y que el móvil lo reciba tal cual."""

from __future__ import annotations

import asyncio

import pytest
from conftest import meal_change

from foodyou_mcp.diary import DiaryError
from foodyou_mcp.sync_client import AccessDenied


def run(coro):
    return asyncio.run(coro)


@pytest.fixture()
def seeded(world):
    run(world["phone"].sync([meal_change("m-desayuno", "Desayuno", 0), meal_change("m-comida", "Comida", 1)]))
    return world


def test_meals_and_an_empty_day(seeded):
    d, t = seeded["diary"], seeded["token"]
    assert [m["name"] for m in run(d.meals(t))] == ["Desayuno", "Comida"]
    day = run(d.day(t, "today"))
    assert day["totals"]["kcal"] == 0 and [m["meal"] for m in day["meals"]] == ["Desayuno", "Comida"]


def test_claude_adds_a_food_and_the_phone_gets_it_as_the_app_stores_it(seeded):
    d, t = seeded["diary"], seeded["token"]
    results = run(d.search(t, "avena"))
    ref = results["database"][0]["ref"]
    assert results["database"][0]["name"] == "Copos de avena (Hacendado)"

    added = run(d.add_food(t, "today", "desayuno", ref, 60))["added"]
    assert added["kcal"] == 223.2 and added["grams"] == 60.0
    assert added["eaten"] is False and added["byAssistant"] is True

    doc = run(seeded["phone"].sync())[("food_entry", added["id"])]
    f = {k: v["value"] for k, v in doc["fields"].items()}
    assert f["meal"] == "m-desayuno" and f["measurement"] == 0 and f["quantity"] == 60.0
    assert f["isEaten"] == 0 and f["createdByAssistant"] == 1
    product = f["food"]["product"]
    assert product["sourceType"] == 4 and product["isLiquid"] == 0
    assert product["energy"] == 372.0 and product["cholesterolMilli"] == 1.0  # g -> mg
    assert product["categories"] == "en:breakfast-cereals"
    assert doc["fields"]["quantity"]["device"] == "mcp"


def test_servings_use_the_products_serving_weight(seeded):
    d, t = seeded["diary"], seeded["token"]
    ref = run(d.search(t, "avena"))["database"][0]["ref"]
    added = run(d.add_food(t, "today", "Desayuno", ref, 2, unit="serving"))["added"]
    assert added["grams"] == 80.0 and added["unit"] == "serving"

    milk = run(d.search(t, "leche"))["database"][0]["ref"]
    with pytest.raises(DiaryError, match="ración"):
        run(d.add_food(t, "today", "Desayuno", milk, 1, unit="serving"))


def test_bad_input_says_how_to_fix_it(seeded):
    d, t = seeded["diary"], seeded["token"]
    ref = run(d.search(t, "avena"))["database"][0]["ref"]
    with pytest.raises(DiaryError, match="Desayuno, Comida"):
        run(d.add_food(t, "today", "Merienda", ref, 50))
    with pytest.raises(DiaryError, match="vuelve a buscar"):
        run(d.add_food(t, "today", "Desayuno", "nope", 50))
    with pytest.raises(DiaryError, match="Unidad"):
        run(d.add_food(t, "today", "Desayuno", ref, 50, unit="tazas"))
    with pytest.raises(DiaryError, match="Fecha"):
        run(d.day(t, "el martes"))


def test_update_mark_eaten_and_undo(seeded):
    d, t = seeded["diary"], seeded["token"]
    ref = run(d.search(t, "avena"))["database"][0]["ref"]
    entry = run(d.add_food(t, "today", "Desayuno", ref, 60))["added"]

    updated = run(d.update_entry(t, entry["id"], amount=80, eaten=True))["updated"]
    assert updated["grams"] == 80.0 and updated["eaten"] is True
    phone = run(seeded["phone"].sync())[("food_entry", entry["id"])]
    assert phone["fields"]["isEaten"]["value"] == 1

    assert "Cambiada" in run(d.undo(t))["undone"]
    back = run(d.day(t, "today"))["meals"][0]["entries"][0]
    assert back["grams"] == 60.0 and back["eaten"] is False

    # Deshacer otra vez quita la entrada que se añadió.
    run(d.undo(t))
    assert run(d.day(t, "today"))["meals"][0]["entries"] == []
    with pytest.raises(DiaryError, match="nada que deshacer"):
        run(d.undo(t))


def test_delete_reaches_the_phone_and_undo_brings_it_back(seeded):
    d, t = seeded["diary"], seeded["token"]
    ref = run(d.search(t, "avena"))["database"][0]["ref"]
    entry = run(d.add_food(t, "today", "Desayuno", ref, 60))["added"]
    run(d.delete_entry(t, entry["id"]))
    assert run(seeded["phone"].sync())[("food_entry", entry["id"])]["deleted"] is True
    run(d.undo(t))
    assert run(seeded["phone"].sync())[("food_entry", entry["id"])]["deleted"] is False


def test_quick_entries_and_their_ingredients(seeded):
    d, t = seeded["diary"], seeded["token"]
    added = run(
        d.add_quick(t, "today", "Comida", "Menú del día", 850, 40, 90, 30, ["lentejas", "pan"], eaten=True)
    )["added"]
    assert added["ingredients"] == ["lentejas", "pan"] and added["eaten"] is True
    doc = run(seeded["phone"].sync())[("manual_entry", added["id"])]
    assert [i["name"] for i in doc["fields"]["ingredients"]["value"]] == ["lentejas", "pan"]
    assert doc["fields"]["energy"]["value"] == 850.0
    day = run(d.day(t, "today"))
    assert day["eatenTotals"]["kcal"] == 850.0


def test_what_was_eaten_before_is_found_first(seeded):
    d, t = seeded["diary"], seeded["token"]
    ref = run(d.search(t, "avena"))["database"][0]["ref"]
    run(d.add_food(t, "yesterday", "Desayuno", ref, 60))
    results = run(d.search(t, "avena"))
    assert results["history"][0]["name"] == "Copos de avena (Hacendado)"
    assert results["history"][0]["source"] == "history"


def test_summary_of_a_week(seeded):
    d, t = seeded["diary"], seeded["token"]
    run(d.add_quick(t, "today", "Comida", "A", 1000, 50, 100, 40))
    run(d.add_quick(t, "yesterday", "Comida", "B", 2000, 100, 200, 80))
    s = run(d.summary(t, "yesterday", "today"))
    assert [day["kcal"] for day in s["days"]] == [2000.0, 1000.0]
    assert s["averageOfLoggedDays"]["kcal"] == 1500.0


def test_without_mcp_access_nothing_works(seeded):
    with pytest.raises(AccessDenied):
        run(seeded["diary"].meals(seeded["bea_token"]))
    with pytest.raises(AccessDenied):
        run(seeded["diary"].meals("fy_mcp_inventado"))


def test_food_searches_go_out_in_the_name_of_the_linked_food_account(seeded):
    from app.db import transaction

    d, t = seeded["diary"], seeded["token"]
    run(d.search(t, "avena"))
    assert d.food_server.on_behalf[-1] is None  # sin cuenta asignada: a nombre del servicio

    with transaction() as conn:
        conn.execute("UPDATE accounts SET food_account = 'ana_foods' WHERE username = 'ana'")
    d.sync._cache(t).status_until = 0  # no esperar el minuto de memoria
    run(d.search(t, "avena"))
    assert d.food_server.on_behalf[-1] == "ana_foods"


def test_the_food_server_gets_the_origin_and_its_refusals_reach_claude():
    import httpx

    from foodyou_mcp.foods import FoodServer, FoodServerRefused

    seen = {}

    def handler(request):
        seen.update(request.headers)
        if request.url.params["query"] == "mucho":
            return httpx.Response(429, json={"detail": "Límite diario alcanzado"})
        return httpx.Response(200, json={"products": [{"name": "Avena"}]})

    server = FoodServer("http://foods", "mcp", "x", transport=httpx.MockTransport(handler))
    assert run(server.search("avena", 5, on_behalf="ana_foods")) == [{"name": "Avena"}]
    assert seen["x-on-behalf-of"] == "ana_foods" and seen["x-search-origin"] == "mcp"
    with pytest.raises(FoodServerRefused, match="límite diario"):
        run(server.search("mucho", 5, on_behalf="ana_foods"))


def _goals_change(tracked, calcium_g=1.2):
    day = {
        "map": {"Energy": 2000, "Proteins": 0.2, "Fats": 0.3, "Carbohydrates": 0.5, "Calcium": calcium_g, "Sugars": 50},
        "isDistribution": True,
    }
    days = {d: day for d in ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")}
    fields = {"separateDays": False, "days": days, "tracked": tracked}
    return {"kind": "goals", "id": "goals", "fields": {k: {"value": v, "clock": 1} for k, v in fields.items()}}


def test_the_day_shows_the_goals_and_how_the_tracked_nutrients_go(seeded):
    d, t = seeded["diary"], seeded["token"]
    assert "goals" not in run(d.day(t, "today"))  # sin objetivos sincronizados, nada
    assert run(d.goals(t, "today"))["available"] is False

    run(seeded["phone"].sync([_goals_change(["Calcium", "Sugars"])]))
    milk = run(d.search(t, "leche"))["database"][0]["ref"]
    run(d.add_food(t, "today", "Desayuno", milk, 250, unit="ml", eaten=True))

    day = run(d.day(t, "today"))
    assert day["goals"] == {"kcal": 2000.0, "proteins": 100.0, "carbohydrates": 250.0, "fats": 66.7}
    calcium, sugars = day["trackedNutrients"]
    assert calcium == {
        "nutrient": "Calcium", "unit": "mg", "target": 1200.0, "total": 300.0, "eaten": 300.0,
        "kind": "minimum", "complete": True,
    }
    assert sugars["kind"] == "limit" and sugars["unit"] == "g" and sugars["target"] == 50.0

    # La avena no dice cuánto calcio tiene: el total pasa a ser un mínimo.
    oats = run(d.search(t, "avena"))["database"][0]["ref"]
    run(d.add_food(t, "today", "Desayuno", oats, 50))
    calcium = run(d.day(t, "today"))["trackedNutrients"][0]
    assert calcium["total"] == 300.0 and calcium["complete"] is False

    goals = run(d.goals(t, "today"))
    assert goals["trackedNutrients"][0] == {"nutrient": "Calcium", "target": 1200.0, "unit": "mg", "kind": "minimum"}
    summary = run(d.summary(t, "today", "today"))
    assert summary["days"][0]["tracked"]["Calcium"]["total"] == 300.0
