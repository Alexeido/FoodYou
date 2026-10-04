"""Lo que el MCP hace además de apuntar: varias cosas a la vez, rehacer, recetas, memoria
compartida, análisis del diario, el borrador y Open Food Facts."""

from __future__ import annotations

import asyncio

import httpx
import pytest
from conftest import meal_change

from foodyou_mcp.diary import DiaryError
from foodyou_mcp.recipes import PRODUCT_COLUMNS, RecipeError


def run(coro):
    return asyncio.run(coro)


@pytest.fixture()
def seeded(world):
    run(world["phone"].sync([meal_change("m-desayuno", "Desayuno", 0), meal_change("m-comida", "Comida", 1)]))
    return world


def _ref(d, t, query, source="database"):
    return run(d.search(t, query))[source][0]["ref"]


def _goals(tracked=("Calcium",)):
    day = {"map": {"Energy": 2000, "Proteins": 0.2, "Fats": 0.3, "Carbohydrates": 0.5, "Calcium": 1.2}, "isDistribution": True}
    days = {d: day for d in ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")}
    fields = {"separateDays": False, "days": days, "tracked": list(tracked)}
    return {"kind": "goals", "id": "goals", "fields": {k: {"value": v, "clock": 1} for k, v in fields.items()}}


def test_several_foods_at_once_undo_together_and_redo(seeded):
    d, t = seeded["diary"], seeded["token"]
    oats, milk = _ref(d, t, "avena"), _ref(d, t, "leche")
    added = run(d.add_foods(t, [
        {"meal": "Desayuno", "ref": oats, "amount": 50},
        {"meal": "Desayuno", "ref": milk, "amount": 200, "unit": "ml"},
        {"meal": "Comida", "ref": oats, "amount": 30, "eaten": True},
    ]))["added"]
    assert [a["meal"] for a in added] == ["Desayuno", "Desayuno", "Comida"]
    assert len(run(d.day(t, "today"))["meals"][0]["entries"]) == 2

    assert "3 alimentos" in run(d.undo(t))["undone"]
    assert all(not m["entries"] for m in run(d.day(t, "today"))["meals"])
    run(d.redo(t))
    assert sum(len(m["entries"]) for m in run(d.day(t, "today"))["meals"]) == 3
    with pytest.raises(DiaryError, match="rehacer"):
        run(d.redo(t))

    # Un error en uno no escribe ninguno.
    with pytest.raises(DiaryError, match="Alimento 2"):
        run(d.add_foods(t, [{"meal": "Desayuno", "ref": oats, "amount": 10}, {"meal": "Cena", "ref": oats, "amount": 10}]))
    assert sum(len(m["entries"]) for m in run(d.day(t, "today"))["meals"]) == 3


def test_mark_eaten_and_delete_several(seeded):
    d, t = seeded["diary"], seeded["token"]
    oats = _ref(d, t, "avena")
    ids = [a["id"] for a in run(d.add_foods(t, [{"meal": "Desayuno", "ref": oats, "amount": 40}] * 2))["added"]]
    run(d.set_eaten(t, ids))
    assert run(d.day(t, "today"))["eatenTotals"]["kcal"] == pytest.approx(297.6)
    run(d.delete_entries(t, ids))
    assert not run(d.day(t, "today"))["meals"][0]["entries"]
    run(d.undo(t))
    assert len(run(d.day(t, "today"))["meals"][0]["entries"]) == 2


def test_recipes_are_created_found_logged_and_reach_the_phone(seeded):
    d, t = seeded["diary"], seeded["token"]
    oats, milk = _ref(d, t, "avena"), _ref(d, t, "leche")
    created = run(d.recipes.create(t, "Gachas", 2, [
        {"ref": oats, "amount": 80},
        {"ref": milk, "amount": 400, "unit": "ml"},
    ]))["created"]
    assert created["servings"] == 2 and created["perServing"]["kcal"] == pytest.approx((372 * 0.8 + 46 * 4) / 2, abs=0.1)

    # El móvil recibe la receta con cada producto entero, en la forma de la biblioteca.
    phone = run(seeded["phone"].sync())
    recipe = phone[("recipe", created["id"])]["fields"]
    product = recipe["ingredients"]["value"][0]["product"]
    assert set(product) == set(PRODUCT_COLUMNS)
    assert product["name"] == "Copos de avena" and product["brand"] == "Hacendado"

    # Una receta dentro de otra.
    run(d.recipes.create(t, "Gachas dobles", 1, [{"recipe_id": "Gachas", "amount": 1, "unit": "serving"}]))
    detail = run(d.recipes.get(t, "Gachas dobles"))
    assert detail["ingredientList"][0]["name"] == "receta: Gachas"

    # Se encuentra al buscar y se apunta como cualquier alimento.
    hit = run(d.search(t, "gachas"))["recipes"][0]
    assert hit["isRecipe"] and hit["recipeId"]
    added = run(d.add_food(t, "today", "Desayuno", hit["ref"], 1, unit="serving"))["added"]
    assert added["type"] == "recipe" and added["kcal"] == pytest.approx(created["perServing"]["kcal"], abs=0.2)

    run(d.recipes.delete(t, "Gachas dobles"))
    assert [r["name"] for r in run(d.recipes.list(t))] == ["Gachas"]
    with pytest.raises(RecipeError, match="Ingrediente 1"):
        run(d.recipes.create(t, "Mala", 1, [{"ref": "nope", "amount": 1}]))


def test_memory_is_shared_with_the_app(seeded):
    d, t = seeded["diary"], seeded["token"]
    # Lo que el asistente de la app recordó en el móvil.
    run(seeded["phone"].sync([{"kind": "memory", "id": "memory", "fields": {"dieta": {"value": "sin lactosa", "clock": 1}}}]))
    assert run(d.memory.all(t)) == {"dieta": "sin lactosa"}
    run(d.memory.remember(t, "objetivo", "más calcio"))
    run(d.memory.forget(t, "dieta"))
    assert run(d.memory.all(t)) == {"objetivo": "más calcio"}
    phone = run(seeded["phone"].sync())
    fields = phone[("memory", "memory")]["fields"]
    assert fields["dieta"]["value"] is None and fields["objetivo"]["value"] == "más calcio"


def test_analysis_of_the_diary(seeded):
    d, t = seeded["diary"], seeded["token"]
    a = seeded["analysis"]
    oats, milk = _ref(d, t, "avena"), _ref(d, t, "leche")
    run(d.add_foods(t, [
        {"meal": "Desayuno", "ref": oats, "amount": 50, "date": "yesterday"},
        {"meal": "Desayuno", "ref": milk, "amount": 250, "unit": "ml", "date": "yesterday", "eaten": True},
        {"meal": "Desayuno", "ref": oats, "amount": 50},
    ]))

    by_day = run(a.totals(t, "yesterday", "today", detail="full"))["groups"]
    assert [g["entries"] for g in by_day] == [2, 1]
    assert by_day[0]["Calcium"] == {"value": 300.0, "unit": "mg", "complete": False}  # la avena no lo dice
    eaten = run(a.totals(t, "yesterday", "today", group_by="meal", only_eaten=True))["groups"]
    assert eaten == [{"group": "Desayuno", "entries": 1, "kcal": 115.0, "proteins": 8.2, "carbohydrates": 11.8, "fats": 4.0}]

    top = run(a.top_foods(t, "yesterday", "today"))["foods"]
    assert top[0]["name"] == "Copos de avena (Hacendado)" and top[0]["times"] == 2 and top[0]["days"] == 2
    assert run(a.top_brands(t, "yesterday", "today"))["brands"][0]["brand"] == "Hacendado"

    calcium = run(a.nutrient_sources(t, "calcio", "yesterday", "today"))
    assert calcium["unit"] == "mg" and calcium["sources"][0] == {"name": "Leche semidesnatada", "amount": 300.0, "share": 100.0}
    assert "Copos de avena (Hacendado)" in calcium["withoutData"]

    found = run(a.find(t, "avena"))
    assert found["found"] == 2 and found["entries"][0]["date"] > found["entries"][1]["date"]
    assert run(a.meal_times(t, "yesterday", "today"))["meals"][0]["meal"] == "Desayuno"


def test_plan_a_day_in_a_draft(seeded):
    d, t, plans = seeded["diary"], seeded["token"], seeded["plans"]
    run(seeded["phone"].sync([_goals()]))
    oats, milk = _ref(d, t, "avena"), _ref(d, t, "leche")
    run(plans.start(t, "today"))
    run(plans.add(t, "Desayuno", oats, 60))
    shown = run(plans.add(t, "Desayuno", milk, 250, "ml"))
    assert len(shown["draft"]) == 2
    assert shown["remaining"]["kcal"] == pytest.approx(2000 - 372 * 0.6 - 115, abs=0.2)
    assert shown["trackedNutrients"][0]["total"] == 300.0
    run(plans.remove(t, 1))
    assert len(run(plans.show(t))["draft"]) == 1
    # Nada en el diario hasta escribirlo.
    assert not run(d.day(t, "today"))["meals"][0]["entries"]
    assert run(plans.commit(t))["written"] == 1
    assert len(run(d.day(t, "today"))["meals"][0]["entries"]) == 1
    with pytest.raises(Exception, match="borrador"):
        run(plans.show(t))


def test_open_food_facts_results_become_products():
    from foodyou_mcp.openfoodfacts import OpenFoodFacts

    def handler(request):
        assert request.url.params["q"] == "leche"
        return httpx.Response(200, json={"hits": [
            {"code": "8480000107749", "product_name": "Leche semidesnatada", "brands": ["Hacendado"],
             "serving_quantity": 250, "nutriments": {"energy-kcal_100g": 46, "proteins_100g": 3.1,
             "carbohydrates_100g": 4.8, "fat_100g": 1.6, "calcium_100g": 0.12, "salt_100g": 0.13}},
            {"code": "1", "product_name": "Sin kcal", "nutriments": {}},
        ]})

    off = OpenFoodFacts(transport=httpx.MockTransport(handler))
    [food] = run(off.search("leche", 5))
    p = food["product"]
    assert p["name"] == "Leche semidesnatada (Hacendado)" and p["brand"] == "Hacendado"
    assert p["calciumMilli"] == pytest.approx(120) and p["servingWeight"] == 250 and p["sourceType"] == 1
