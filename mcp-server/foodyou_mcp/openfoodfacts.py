"""Open Food Facts, el catálogo abierto que también usa la app, para lo que no está en la base
de datos propia: productos de supermercado con su código de barras y su marca.

Usa su buscador (search.openfoodfacts.org). Los nutrientes vienen por 100 g y en gramos; aquí
se pasan a las columnas y unidades del diario, como hace la app al guardar un producto.
"""

from __future__ import annotations

from typing import Any, Optional

import httpx

SOURCE_OPEN_FOOD_FACTS = 1  # FoodSourceType de la app

# Clave de Open Food Facts (por 100 g, en g) -> columna del diario y por cuánto multiplicar.
_NUTRIMENTS: dict[str, tuple[str, float]] = {
    "proteins_100g": ("proteins", 1),
    "carbohydrates_100g": ("carbohydrates", 1),
    "fat_100g": ("fats", 1),
    "saturated-fat_100g": ("saturatedFats", 1),
    "trans-fat_100g": ("transFats", 1),
    "monounsaturated-fat_100g": ("monounsaturatedFats", 1),
    "polyunsaturated-fat_100g": ("polyunsaturatedFats", 1),
    "omega-3-fat_100g": ("omega3", 1),
    "omega-6-fat_100g": ("omega6", 1),
    "sugars_100g": ("sugars", 1),
    "added-sugars_100g": ("addedSugars", 1),
    "fiber_100g": ("dietaryFiber", 1),
    "soluble-fiber_100g": ("solubleFiber", 1),
    "insoluble-fiber_100g": ("insolubleFiber", 1),
    "salt_100g": ("salt", 1),
    "cholesterol_100g": ("cholesterolMilli", 1_000),
    "caffeine_100g": ("caffeineMilli", 1_000),
    "vitamin-a_100g": ("vitaminAMicro", 1_000_000),
    "vitamin-b1_100g": ("vitaminB1Milli", 1_000),
    "vitamin-b2_100g": ("vitaminB2Milli", 1_000),
    "vitamin-pp_100g": ("vitaminB3Milli", 1_000),
    "pantothenic-acid_100g": ("vitaminB5Milli", 1_000),
    "vitamin-b6_100g": ("vitaminB6Milli", 1_000),
    "biotin_100g": ("vitaminB7Micro", 1_000_000),
    "vitamin-b9_100g": ("vitaminB9Micro", 1_000_000),
    "vitamin-b12_100g": ("vitaminB12Micro", 1_000_000),
    "vitamin-c_100g": ("vitaminCMilli", 1_000),
    "vitamin-d_100g": ("vitaminDMicro", 1_000_000),
    "vitamin-e_100g": ("vitaminEMilli", 1_000),
    "vitamin-k_100g": ("vitaminKMicro", 1_000_000),
    "manganese_100g": ("manganeseMilli", 1_000),
    "magnesium_100g": ("magnesiumMilli", 1_000),
    "potassium_100g": ("potassiumMilli", 1_000),
    "calcium_100g": ("calciumMilli", 1_000),
    "copper_100g": ("copperMilli", 1_000),
    "zinc_100g": ("zincMilli", 1_000),
    "sodium_100g": ("sodiumMilli", 1_000),
    "iron_100g": ("ironMilli", 1_000),
    "phosphorus_100g": ("phosphorusMilli", 1_000),
    "selenium_100g": ("seleniumMicro", 1_000_000),
    "iodine_100g": ("iodineMicro", 1_000_000),
    "chromium_100g": ("chromiumMicro", 1_000_000),
}


def _num(value: Any) -> Optional[float]:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    try:
        return float(value) if value not in (None, "") else None
    except (TypeError, ValueError):
        return None


def snapshot(hit: dict) -> Optional[dict]:
    """Un resultado del buscador como copia de producto del diario, o None si no trae kcal."""
    nutriments = hit.get("nutriments") or {}
    energy = _num(nutriments.get("energy-kcal_100g"))
    if energy is None:
        kj = _num(nutriments.get("energy-kj_100g")) or _num(nutriments.get("energy_100g"))
        energy = kj / 4.184 if kj is not None else None
    name = (hit.get("product_name") or "").strip()
    if energy is None or not name:
        return None
    brands = hit.get("brands") or []
    brand = (brands[0] if isinstance(brands, list) and brands else str(brands or "")).strip() or None
    code = str(hit.get("code") or "") or None
    product: dict[str, Any] = {
        # La app enseña la marca en su propia línea si el nombre es "Nombre (Marca)".
        "name": f"{name} ({brand})" if brand and brand.lower() not in name.lower() else name,
        "brand": brand,
        "barcode": code,
        "packageWeight": _num(hit.get("product_quantity")),
        "servingWeight": _num(hit.get("serving_quantity")),
        "isLiquid": 0,
        "sourceType": SOURCE_OPEN_FOOD_FACTS,
        "sourceUrl": f"https://world.openfoodfacts.org/product/{code}" if code else None,
        "note": None,
        "categories": None,
        "energy": round(energy, 2),
    }
    for key, (column, scale) in _NUTRIMENTS.items():
        value = _num(nutriments.get(key))
        product[column] = value * scale if value is not None else None
    return {"product": product}


class OpenFoodFacts:
    def __init__(self, url: str = "https://search.openfoodfacts.org", language: str = "es", transport=None) -> None:
        self._url = url.rstrip("/")
        self._language = language
        self._transport = transport

    async def search(self, query: str, limit: int) -> list[dict]:
        params = {
            "q": query,
            "page_size": max(1, min(limit * 2, 40)),  # algunos vienen sin kcal y se descartan
            "langs": self._language,
            "fields": "code,product_name,brands,nutriments,serving_quantity,product_quantity",
        }
        headers = {"User-Agent": "FoodYou-MCP/1.0 (https://foods.alexeido.com)"}
        async with httpx.AsyncClient(timeout=20, transport=self._transport) as client:
            r = await client.get(f"{self._url}/search", params=params, headers=headers)
        r.raise_for_status()
        foods = [s for s in (snapshot(hit) for hit in r.json().get("hits") or []) if s]
        return foods[:limit]
