"""Buscar alimentos para añadirlos al diario.

Dos fuentes: el servidor de alimentos (si está configurado), con el mismo contrato que usa
la app (`GET /search`), y el propio historial del diario de la cuenta, que es lo que más
se repite ("lo de siempre").

Cada resultado se guarda unos minutos bajo una referencia corta (`ref`) que Claude pasa
luego a `add_food`: así no tiene que copiar el producto entero con sus nutrientes.
"""

from __future__ import annotations

import secrets
import time
from typing import Any, Optional

import httpx

from foodyou_mcp import nutrition

# Unidades del contrato del servidor de alimentos (gramos) a las columnas del diario.
_GRAMS = [
    "energy", "proteins", "carbohydrates", "fats", "saturatedFats", "transFats",
    "monounsaturatedFats", "polyunsaturatedFats", "omega3", "omega6", "sugars",
    "addedSugars", "dietaryFiber", "solubleFiber", "insolubleFiber", "salt",
]
_MILLI = {
    "cholesterol": "cholesterolMilli", "caffeine": "caffeineMilli",
    "vitaminB1": "vitaminB1Milli", "vitaminB2": "vitaminB2Milli", "vitaminB3": "vitaminB3Milli",
    "vitaminB5": "vitaminB5Milli", "vitaminB6": "vitaminB6Milli", "vitaminC": "vitaminCMilli",
    "vitaminE": "vitaminEMilli", "manganese": "manganeseMilli", "magnesium": "magnesiumMilli",
    "potassium": "potassiumMilli", "calcium": "calciumMilli", "copper": "copperMilli",
    "zinc": "zincMilli", "sodium": "sodiumMilli", "iron": "ironMilli",
    "phosphorus": "phosphorusMilli",
}
_MICRO = {
    "vitaminA": "vitaminAMicro", "vitaminB7": "vitaminB7Micro", "vitaminB9": "vitaminB9Micro",
    "vitaminB12": "vitaminB12Micro", "vitaminD": "vitaminDMicro", "vitaminK": "vitaminKMicro",
    "selenium": "seleniumMicro", "iodine": "iodineMicro", "chromium": "chromiumMicro",
}
SOURCE_CUSTOM = 4
_REF_TTL = 3600


def product_snapshot(product: dict) -> dict:
    """Un producto del contrato del servidor de alimentos, como copia del diario."""
    facts = product.get("nutritionFacts") or {}
    name = product.get("name") or "?"
    brand = product.get("brand")
    snapshot: dict[str, Any] = {
        # La app enseña la marca en su propia línea si el nombre es "Nombre (Marca)".
        "name": f"{name} ({brand})" if brand and brand.lower() not in name.lower() else name,
        # La copia del diario no la guarda (va en el nombre); una receta de la biblioteca, sí.
        "brand": brand,
        "packageWeight": product.get("packageWeight"),
        "servingWeight": product.get("servingWeight"),
        "isLiquid": 1 if product.get("isLiquid") else 0,
        "sourceType": SOURCE_CUSTOM,
        "sourceUrl": product.get("url"),
        "note": None,
        "categories": ",".join(product.get("categories") or []) or None,
    }
    for key in _GRAMS:
        snapshot[key] = facts.get(key)
    for key, column in _MILLI.items():
        snapshot[column] = facts[key] * 1000 if isinstance(facts.get(key), (int, float)) else None
    for key, column in _MICRO.items():
        snapshot[column] = facts[key] * 1_000_000 if isinstance(facts.get(key), (int, float)) else None
    return {"product": snapshot}


class FoodRefs:
    """Resultados de búsqueda recordados unos minutos bajo una referencia corta."""

    def __init__(self) -> None:
        self._refs: dict[str, tuple[float, dict]] = {}

    def remember(self, food: dict) -> str:
        now = time.monotonic()
        self._refs = {k: v for k, v in self._refs.items() if now - v[0] < _REF_TTL}
        ref = secrets.token_hex(4)
        self._refs[ref] = (now, food)
        return ref

    def get(self, ref: str) -> Optional[dict]:
        hit = self._refs.get(ref)
        if hit is None or time.monotonic() - hit[0] > _REF_TTL:
            return None
        return hit[1]


class FoodServer:
    def __init__(self, url: str, user: str, password: str, transport=None) -> None:
        self._url = url.rstrip("/")
        self._auth = (user, password) if user else None
        self._transport = transport

    @property
    def configured(self) -> bool:
        return bool(self._url)

    async def search(self, query: str, limit: int, on_behalf: Optional[str] = None) -> list[dict]:
        """Busca como cuenta de servicio. Con `on_behalf`, la búsqueda cuenta para esa cuenta
        de foods (su límite y sus estadísticas); siempre queda marcada como hecha por Claude."""
        headers = {"X-Search-Origin": "mcp", "User-Agent": "FoodYou-MCP"}
        if on_behalf:
            headers["X-On-Behalf-Of"] = on_behalf
        async with httpx.AsyncClient(timeout=30, auth=self._auth, transport=self._transport) as client:
            r = await client.get(f"{self._url}/search", params={"query": query, "page_size": limit}, headers=headers)
        if r.status_code == 429:
            raise FoodServerRefused(
                f"La cuenta de alimentos{f' {on_behalf}' if on_behalf else ''} ha llegado a su límite diario de búsquedas."
            )
        if r.status_code in (401, 403):
            try:
                detail = r.json().get("detail")
            except ValueError:
                detail = None
            raise FoodServerRefused(f"El servidor de alimentos no deja buscar: {detail or r.status_code}.")
        r.raise_for_status()
        return r.json().get("products", [])[:limit]


class FoodServerRefused(Exception):
    """El servidor de alimentos dijo que no (límite, cuenta desconocida...): se le explica a Claude."""


def describe(food: dict, ref: str, source: str) -> dict:
    inner = food.get("product") or food.get("recipe") or {}
    return {
        "ref": ref,
        "name": nutrition.food_name(food),
        "source": source,
        "per100g": nutrition.per_100(food),
        "servingWeight": inner.get("servingWeight"),
        "packageWeight": inner.get("packageWeight"),
        "isLiquid": bool(inner.get("isLiquid")),
        "isRecipe": "recipe" in food,
    }
