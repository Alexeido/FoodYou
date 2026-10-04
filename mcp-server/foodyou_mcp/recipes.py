"""Las recetas de la biblioteca de la app, sincronizadas (docs/sync/protocol.md, kind `recipe`).

Una receta es un nombre, unas raciones y sus ingredientes: cada uno un producto (copiado entero,
con todas sus columnas) o una referencia a otra receta. Lo que Claude crea aquí aparece en la
app como una receta más, y lo que se crea en la app se puede apuntar desde aquí.
"""

from __future__ import annotations

import uuid
from typing import Any, Optional

from foodyou_mcp import nutrition
from foodyou_mcp.sync_client import Document, change

RECIPE = "recipe"

# Las columnas de un producto de la biblioteca tal como viaja dentro de una receta: todas menos
# el id y lo que solo importa en cada móvil (favorito, editado). Siempre todas, para que lo que
# el móvil lee de vuelta sea idéntico y nada rebote.
PRODUCT_COLUMNS = (
    "name", "brand", "barcode", "sourceBarcode", "packageWeight", "servingWeight", "note",
    "sourceType", "sourceUrl", "isLiquid", "categories", "energy", "proteins", "fats",
    "saturatedFats", "transFats", "monounsaturatedFats", "polyunsaturatedFats", "omega3",
    "omega6", "carbohydrates", "sugars", "addedSugars", "dietaryFiber", "solubleFiber",
    "insolubleFiber", "salt", "cholesterolMilli", "caffeineMilli", "vitaminAMicro",
    "vitaminB1Milli", "vitaminB2Milli", "vitaminB3Milli", "vitaminB5Milli", "vitaminB6Milli",
    "vitaminB7Micro", "vitaminB9Micro", "vitaminB12Micro", "vitaminCMilli", "vitaminDMicro",
    "vitaminEMilli", "vitaminKMicro", "manganeseMilli", "magnesiumMilli", "potassiumMilli",
    "calciumMilli", "copperMilli", "zincMilli", "sodiumMilli", "ironMilli", "phosphorusMilli",
    "seleniumMicro", "iodineMicro", "chromiumMicro",
)
# Lo que la copia de un producto en el diario no tiene (la marca va en el nombre).
_LIBRARY_ONLY = ("brand", "barcode", "sourceBarcode")


class RecipeError(ValueError):
    pass


def library_product(product: dict) -> dict:
    """Un producto (de una búsqueda) como ingrediente de una receta de la biblioteca."""
    out = {c: product.get(c) for c in PRODUCT_COLUMNS}
    brand = out.get("brand")
    name = str(out.get("name") or "?")
    # En la biblioteca la marca va aparte: "Leche (Hacendado)" -> "Leche" + "Hacendado".
    if brand and name.endswith(f" ({brand})"):
        out["name"] = name[: -len(f" ({brand})")]
    out["isLiquid"] = int(out.get("isLiquid") or 0)
    out["sourceType"] = int(out.get("sourceType") or 0)
    return out


def diary_product(product: dict) -> dict:
    """Un producto de la biblioteca como copia en una entrada del diario."""
    out = {k: v for k, v in product.items() if k not in _LIBRARY_ONLY}
    brand = product.get("brand")
    if brand and brand.lower() not in str(product.get("name", "")).lower():
        out["name"] = f"{product.get('name')} ({brand})"
    return out


class Recipes:
    def __init__(self, diary) -> None:
        self.diary = diary

    @staticmethod
    def live(docs: dict) -> list[Document]:
        return sorted(
            (d for (k, _), d in docs.items() if k == RECIPE and not d.deleted),
            key=lambda d: str(d.value("name", "")).lower(),
        )

    def _recipe(self, docs: dict, recipe_id: str) -> Document:
        doc = docs.get((RECIPE, recipe_id))
        if doc is None or doc.deleted:
            for d in self.live(docs):
                if str(d.value("name", "")).strip().lower() == recipe_id.strip().lower():
                    return d
            raise RecipeError(f"No hay ninguna receta '{recipe_id}'. Mira las que hay con get_recipes.")
        return doc

    def diary_food(self, docs: dict, doc: Document, depth: int = 0) -> dict:
        """La receta como copia para una entrada del diario: los ingredientes expandidos, con
        las recetas de dentro también copiadas."""
        if depth > 5:
            raise RecipeError("Recetas dentro de recetas demasiado profundas.")
        ingredients = []
        for i, ingredient in enumerate(doc.value("ingredients") or []):
            if isinstance(ingredient.get("product"), dict):
                food = {"product": diary_product(ingredient["product"])}
            elif ingredient.get("recipe"):
                sub = docs.get((RECIPE, ingredient["recipe"]))
                if sub is None or sub.deleted:
                    continue
                food = self.diary_food(docs, sub, depth + 1)
            else:
                continue
            ingredients.append({"measurement": ingredient.get("measurement", 0), "quantity": ingredient.get("quantity"), "food": food})
        return {
            "recipe": {
                "name": doc.value("name"),
                "servings": doc.value("servings") or 1,
                "isLiquid": doc.value("isLiquid") or 0,
                "note": doc.value("note"),
                "ingredients": ingredients,
            }
        }

    def summary(self, docs: dict, doc: Document) -> dict:
        food = self.diary_food(docs, doc)
        weight, totals = nutrition.food_totals(food)
        servings = max(1, int(doc.value("servings") or 1))
        return {
            "id": doc.id,
            "name": doc.value("name"),
            "servings": servings,
            "ingredients": len(doc.value("ingredients") or []),
            "favorite": bool(doc.value("isFavorite")),
            "totalGrams": round(weight, 1),
            "perServing": {
                "grams": round(weight / servings, 1),
                "kcal": round(totals["energy"] / servings, 1),
                "proteins": round(totals["proteins"] / servings, 1),
                "carbohydrates": round(totals["carbohydrates"] / servings, 1),
                "fats": round(totals["fats"] / servings, 1),
            },
        }

    def search_hits(self, docs: dict, needle: str, limit: int) -> list[tuple[dict, Document]]:
        hits = [d for d in self.live(docs) if needle in str(d.value("name", "")).lower()]
        return [(self.diary_food(docs, d), d) for d in hits[:limit]]

    # --- Leer ---------------------------------------------------------------------------

    async def list(self, token: str, query: Optional[str] = None) -> list[dict]:
        docs = await self.diary.sync.documents(token)
        needle = (query or "").strip().lower()
        return [self.summary(docs, d) for d in self.live(docs) if needle in str(d.value("name", "")).lower()]

    async def get(self, token: str, recipe_id: str) -> dict:
        docs = await self.diary.sync.documents(token)
        doc = self._recipe(docs, recipe_id)
        ingredients = []
        for ingredient in doc.value("ingredients") or []:
            unit = nutrition.UNIT_NAMES.get(int(ingredient.get("measurement", 0)), "g")
            if isinstance(ingredient.get("product"), dict):
                name = diary_product(ingredient["product"]).get("name")
            else:
                sub = docs.get((RECIPE, ingredient.get("recipe") or ""))
                name = f"receta: {sub.value('name')}" if sub else "receta borrada"
            ingredients.append({"name": name, "amount": ingredient.get("quantity"), "unit": unit})
        return {**self.summary(docs, doc), "note": doc.value("note"), "ingredientList": ingredients}

    # --- Escribir -----------------------------------------------------------------------

    async def create(
        self,
        token: str,
        name: str,
        servings: int,
        ingredients: list[dict],
        note: Optional[str] = None,
        liquid: bool = False,
    ) -> dict:
        """ingredients: [{"ref": de search_food | "recipe_id": de get_recipes, "amount", "unit"}]."""
        if not name.strip():
            raise RecipeError("Ponle un nombre a la receta.")
        if servings < 1:
            raise RecipeError("Al menos una ración.")
        if not ingredients:
            raise RecipeError("Una receta necesita ingredientes.")
        docs = await self.diary.sync.documents(token)
        out: list[dict[str, Any]] = []
        for n, item in enumerate(ingredients, start=1):
            amount = float(item.get("amount") or 0)
            unit = str(item.get("unit") or "g")
            try:
                if item.get("recipe_id"):
                    sub = self._recipe(docs, str(item["recipe_id"]))
                    food = self.diary_food(docs, sub)
                    measurement = self.diary._measurement(food, amount, unit)
                    out.append({"measurement": measurement, "quantity": amount, "recipe": sub.id})
                    continue
                food = self.diary._food(str(item.get("ref", "")))
                if "product" not in food:
                    raise RecipeError("Ese resultado es una receta: pásalo como recipe_id.")
                measurement = self.diary._measurement(food, amount, unit)
                out.append({"measurement": measurement, "quantity": amount, "product": library_product(food["product"])})
            except (RecipeError, ValueError) as exc:
                raise RecipeError(f"Ingrediente {n}: {exc}") from None
        recipe_id = uuid.uuid4().hex
        values = {
            "name": name.strip(),
            "servings": int(servings),
            "note": note,
            "isLiquid": 1 if liquid else 0,
            "isFavorite": 0,
            "ingredients": out,
            "_deleted": False,
        }
        docs = await self.diary._commit(
            token,
            [change(RECIPE, recipe_id, values)],
            f"Creada la receta {name.strip()}",
            [{"kind": RECIPE, "id": recipe_id, "values": {"_deleted": True}}],
            [{"kind": RECIPE, "id": recipe_id, "values": {"_deleted": False}}],
        )
        return {"created": self.summary(docs, docs[(RECIPE, recipe_id)])}

    async def delete(self, token: str, recipe_id: str) -> dict:
        docs = await self.diary.sync.documents(token)
        doc = self._recipe(docs, recipe_id)
        await self.diary._commit(
            token,
            [change(RECIPE, doc.id, {"_deleted": True}, doc)],
            f"Borrada la receta {doc.value('name')}",
            [{"kind": RECIPE, "id": doc.id, "values": {"_deleted": False}}],
            [{"kind": RECIPE, "id": doc.id, "values": {"_deleted": True}}],
        )
        return {"deleted": doc.value("name")}


# --- Memoria del asistente (kind `memory`, un documento, un campo por cosa) -------------------

MEMORY, MEMORY_ID = "memory", "memory"


class Memory:
    """Lo que el asistente recuerda de la persona. Es la misma memoria que la del asistente de
    la app: lo que se cuenta a uno, lo sabe el otro."""

    def __init__(self, diary) -> None:
        self.diary = diary

    async def all(self, token: str) -> dict:
        docs = await self.diary.sync.documents(token)
        doc = docs.get((MEMORY, MEMORY_ID))
        if doc is None:
            return {}
        return {k: v for k, v in doc.values().items() if isinstance(v, str)}

    async def remember(self, token: str, key: str, value: str) -> dict:
        key, value = key.strip()[:60], value.strip()[:500]
        if not key or not value:
            raise RecipeError("Hace falta qué recordar (clave) y el qué (valor).")
        docs = await self.diary.sync.documents(token)
        await self.diary.sync.push(token, [change(MEMORY, MEMORY_ID, {key: value}, docs.get((MEMORY, MEMORY_ID)))])
        return {"remembered": {key: value}}

    async def forget(self, token: str, key: str) -> dict:
        docs = await self.diary.sync.documents(token)
        doc = docs.get((MEMORY, MEMORY_ID))
        if doc is None or not isinstance(doc.value(key), str):
            raise RecipeError(f"No hay nada recordado como '{key}'.")
        await self.diary.sync.push(token, [change(MEMORY, MEMORY_ID, {key: None}, doc)])
        return {"forgotten": key}
