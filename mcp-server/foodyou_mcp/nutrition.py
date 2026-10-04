"""Cuánto aporta una entrada del diario, calculado como lo hace la app.

Un producto guarda sus nutrientes por 100 g (o 100 ml). El peso de una entrada sale de su
unidad: gramos y mililitros tal cual, raciones y envases con el peso de ración o de envase
del producto, onzas convertidas. Una receta suma lo que aporta cada ingrediente para su peso
entero y lo reparte según la parte que se come.
"""

from __future__ import annotations

from typing import Optional

GRAM, PACKAGE, SERVING, MILLILITER, OUNCE, FLUID_OUNCE = 0, 1, 2, 3, 4, 5
UNITS = {"g": GRAM, "ml": MILLILITER, "serving": SERVING, "package": PACKAGE, "oz": OUNCE, "floz": FLUID_OUNCE}
UNIT_NAMES = {v: k for k, v in UNITS.items()}

MACROS = ("energy", "proteins", "carbohydrates", "fats")


def _num(value) -> Optional[float]:
    return float(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else None


def food_totals(food: dict) -> tuple[float, dict[str, float]]:
    """(peso total en g, macros totales) de una copia de alimento entera.

    Para un producto, el "total" es por 100 g; para una receta, el plato entero."""
    if "product" in food:
        p = food["product"]
        return 100.0, {m: _num(p.get(m)) or 0.0 for m in MACROS}
    recipe = food.get("recipe") or {}
    weight = 0.0
    totals = {m: 0.0 for m in MACROS}
    for ingredient in recipe.get("ingredients") or []:
        sub = ingredient.get("food") or {}
        grams = weight_of(sub, ingredient.get("measurement", GRAM), _num(ingredient.get("quantity")) or 0.0)
        if grams is None:
            continue
        sub_weight, sub_totals = food_totals(sub)
        if sub_weight <= 0:
            continue
        weight += grams
        for m in MACROS:
            totals[m] += sub_totals[m] * grams / sub_weight
    return weight, totals


def weight_of(food: dict, measurement: int, quantity: float) -> Optional[float]:
    """Gramos (o ml) de `quantity` en esa unidad, o None si el alimento no lo sabe."""
    if measurement in (GRAM, MILLILITER):
        return quantity
    if measurement == OUNCE:
        return quantity * 28.3495
    if measurement == FLUID_OUNCE:
        return quantity * 29.5735
    if "product" in food:
        p = food["product"]
        unit_weight = _num(p.get("servingWeight" if measurement == SERVING else "packageWeight"))
    else:
        total, _ = food_totals(food)
        servings = _num((food.get("recipe") or {}).get("servings")) or 1.0
        unit_weight = total / servings if measurement == SERVING else total
    return unit_weight * quantity if unit_weight else None


def entry_nutrition(fields: dict) -> Optional[dict]:
    """kcal y macros de una entrada `food_entry`, o None si no se puede calcular."""
    food = fields.get("food")
    if not isinstance(food, dict):
        return None
    grams = weight_of(food, int(fields.get("measurement", GRAM)), _num(fields.get("quantity")) or 0.0)
    if grams is None:
        return None
    total_weight, totals = food_totals(food)
    if total_weight <= 0:
        return None
    factor = grams / total_weight
    return {"grams": round(grams, 1), **{m: round(totals[m] * factor, 1) for m in MACROS}}


def food_name(food: dict) -> str:
    inner = food.get("product") or food.get("recipe") or {}
    return str(inner.get("name") or "?")


def per_100(food: dict) -> dict:
    weight, totals = food_totals(food)
    if weight <= 0:
        return {m: None for m in MACROS}
    return {m: round(totals[m] * 100 / weight, 1) for m in MACROS}


# --- Micronutrientes (los objetivos que la persona quiere cumplir) ---------------------------
#
# Los objetivos de la app van en gramos y por nombre de campo ("Calcium"); el diario guarda cada
# nutriente en su columna y su unidad, por 100 g ("calciumMilli" = mg). Lo que no lleva sufijo
# va en gramos.

_GRAM_FIELDS = (
    "SaturatedFats", "TransFats", "MonounsaturatedFats", "PolyunsaturatedFats", "Omega3", "Omega6",
    "Sugars", "AddedSugars", "DietaryFiber", "SolubleFiber", "InsolubleFiber", "Salt",
)
_MILLI_FIELDS = (
    "Cholesterol", "Caffeine", "VitaminB1", "VitaminB2", "VitaminB3", "VitaminB5", "VitaminB6",
    "VitaminC", "VitaminE", "Manganese", "Magnesium", "Potassium", "Calcium", "Copper", "Zinc",
    "Sodium", "Iron", "Phosphorus",
)
_MICRO_FIELDS = (
    "VitaminA", "VitaminB7", "VitaminB9", "VitaminB12", "VitaminD", "VitaminK", "Selenium",
    "Iodine", "Chromium",
)
# Techos, no metas: pasarse es lo malo.
LIMIT_FIELDS = {"Sugars", "AddedSugars", "SaturatedFats", "TransFats", "Salt", "Sodium", "Cholesterol", "Caffeine"}


def nutrient_column(field: str) -> Optional[tuple[str, str, float]]:
    """(columna del diario, unidad, cuántas unidades es un gramo) de un campo, o None."""
    base = field[0].lower() + field[1:]
    if field in _GRAM_FIELDS:
        return base, "g", 1.0
    if field in _MILLI_FIELDS:
        return base + "Milli", "mg", 1_000.0
    if field in _MICRO_FIELDS:
        return base + "Micro", "µg", 1_000_000.0
    return None


def _food_amount(food: dict, column: str) -> tuple[float, float, bool]:
    """(peso de referencia, cantidad de la columna en ese peso, si se sabe del todo)."""
    if "product" in food:
        value = _num(food["product"].get(column))
        return 100.0, value or 0.0, value is not None
    recipe = food.get("recipe") or {}
    weight, amount, complete = 0.0, 0.0, True
    for ingredient in recipe.get("ingredients") or []:
        sub = ingredient.get("food") or {}
        grams = weight_of(sub, ingredient.get("measurement", GRAM), _num(ingredient.get("quantity")) or 0.0)
        if grams is None:
            complete = False
            continue
        sub_weight, sub_amount, sub_complete = _food_amount(sub, column)
        if sub_weight <= 0:
            continue
        weight += grams
        amount += sub_amount * grams / sub_weight
        complete = complete and sub_complete
    return weight, amount, complete


def entry_nutrient(kind: str, fields: dict, column: str) -> tuple[float, bool]:
    """Cuánto aporta una entrada de una columna (en su unidad) y si el dato está completo.

    Muchos productos no traen todos los micronutrientes: entonces la cifra es un mínimo."""
    if kind == "manual_entry":
        value = _num(fields.get(column))
        return value or 0.0, value is not None
    food = fields.get("food")
    if not isinstance(food, dict):
        return 0.0, False
    grams = weight_of(food, int(fields.get("measurement", GRAM)), _num(fields.get("quantity")) or 0.0)
    if grams is None:
        return 0.0, False
    weight, amount, complete = _food_amount(food, column)
    if weight <= 0:
        return 0.0, False
    return amount * grams / weight, complete


# --- Todos los nutrientes de una entrada ----------------------------------------------------

# Columna del diario -> (nombre del campo en la app, unidad).
COLUMNS: dict[str, tuple[str, str]] = {
    "energy": ("Energy", "kcal"),
    "proteins": ("Proteins", "g"),
    "carbohydrates": ("Carbohydrates", "g"),
    "fats": ("Fats", "g"),
}
for _field in _GRAM_FIELDS + _MILLI_FIELDS + _MICRO_FIELDS:
    _column, _unit, _ = nutrient_column(_field)
    COLUMNS[_column] = (_field, _unit)

BASIC = ("energy", "proteins", "carbohydrates", "fats")
EXTENDED = BASIC + ("saturatedFats", "sugars", "dietaryFiber", "salt", "cholesterolMilli", "caffeineMilli")
FULL = tuple(COLUMNS)


def column_for(name: str) -> Optional[str]:
    """La columna de un nutriente dicho de cualquier forma: "Calcium", "calcium", "calciumMilli",
    "kcal", "fibra"... o None."""
    key = name.strip().lower().replace(" ", "").replace("_", "")
    aliases = {
        "kcal": "energy", "calorias": "energy", "calories": "energy", "energia": "energy",
        "proteina": "proteins", "proteinas": "proteins", "protein": "proteins",
        "carbohidratos": "carbohydrates", "carbs": "carbohydrates", "hidratos": "carbohydrates",
        "grasa": "fats", "grasas": "fats", "fat": "fats",
        "fibra": "dietaryFiber", "fiber": "dietaryFiber", "fibre": "dietaryFiber",
        "azucar": "sugars", "azucares": "sugars", "sugar": "sugars", "sal": "salt",
        "calcio": "calciumMilli", "hierro": "ironMilli", "potasio": "potassiumMilli",
        "magnesio": "magnesiumMilli", "sodio": "sodiumMilli", "zinc": "zincMilli",
        "fosforo": "phosphorusMilli", "yodo": "iodineMicro", "colesterol": "cholesterolMilli",
        "cafeina": "caffeineMilli", "grasasaturada": "saturatedFats", "saturadas": "saturatedFats",
    }
    if key in aliases:
        return aliases[key]
    for column, (field, _) in COLUMNS.items():
        if key in (column.lower(), field.lower()):
            return column
    return None


def _food_vector(food: dict, columns: tuple[str, ...]) -> tuple[float, dict[str, tuple[float, bool]]]:
    """(peso de referencia, {columna: (cantidad en ese peso, si se sabe del todo)})."""
    if "product" in food:
        p = food["product"]
        out = {}
        for c in columns:
            value = _num(p.get(c))
            out[c] = (value or 0.0, value is not None)
        return 100.0, out
    recipe = food.get("recipe") or {}
    weight = 0.0
    out = {c: (0.0, True) for c in columns}
    for ingredient in recipe.get("ingredients") or []:
        sub = ingredient.get("food") or {}
        grams = weight_of(sub, ingredient.get("measurement", GRAM), _num(ingredient.get("quantity")) or 0.0)
        if grams is None:
            out = {c: (v, False) for c, (v, _) in out.items()}
            continue
        sub_weight, sub_vector = _food_vector(sub, columns)
        if sub_weight <= 0:
            continue
        weight += grams
        for c in columns:
            amount, known = sub_vector[c]
            total, ok = out[c]
            out[c] = (total + amount * grams / sub_weight, ok and known)
    return weight, out


def entry_vector(kind: str, fields: dict, columns: tuple[str, ...] = FULL) -> dict[str, tuple[float, bool]]:
    """Lo que aporta una entrada de cada columna, en su unidad, y si el dato está completo."""
    if kind == "manual_entry":
        out = {}
        for c in columns:
            value = _num(fields.get(c))
            out[c] = (value or 0.0, value is not None)
        return out
    food = fields.get("food")
    unknown = {c: (0.0, False) for c in columns}
    if not isinstance(food, dict):
        return unknown
    grams = weight_of(food, int(fields.get("measurement", GRAM)), _num(fields.get("quantity")) or 0.0)
    if grams is None:
        return unknown
    weight, vector = _food_vector(food, columns)
    if weight <= 0:
        return unknown
    return {c: (amount * grams / weight, known) for c, (amount, known) in vector.items()}


def food_vector_per(food: dict, measurement: int, quantity: float, columns: tuple[str, ...] = FULL) -> Optional[dict[str, tuple[float, bool]]]:
    """Lo mismo para un alimento y una cantidad que aún no están en el diario (el borrador)."""
    return entry_vector("food_entry", {"food": food, "measurement": measurement, "quantity": quantity}, columns) \
        if weight_of(food, measurement, quantity) is not None else None


def present(vector: dict[str, tuple[float, bool]], columns: tuple[str, ...]) -> dict:
    """Para Claude: lo básico como números; lo demás con su unidad y si está completo."""
    out: dict = {}
    for c in columns:
        amount, known = vector.get(c, (0.0, False))
        if c in BASIC:
            out["kcal" if c == "energy" else c] = round(amount, 1)
        else:
            field, unit = COLUMNS[c]
            out[field] = {"value": round(amount, 1), "unit": unit, **({} if known else {"complete": False})}
    return out


def add_vectors(total: dict[str, tuple[float, bool]], other: dict[str, tuple[float, bool]]) -> dict:
    for c, (amount, known) in other.items():
        t, ok = total.get(c, (0.0, True))
        total[c] = (t + amount, ok and known)
    return total
