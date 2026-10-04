"""Un borrador para planificar un día sin tocar el diario: se prueba, se suma y, cuando cuadra,
se escribe de una vez (un solo paso de deshacer) o se tira.

Como el borrador del asistente de la app (padFromDay, padAdd, padTotals, padCommit...). Vive en
la memoria del MCP, uno por cuenta, y caduca a las pocas horas o si el servidor se reinicia: es
para planificar en una conversación, no para guardar.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Optional

from foodyou_mcp import nutrition

_TTL = 6 * 3600


class PlanError(ValueError):
    pass


@dataclass
class _Item:
    n: int
    meal_id: str
    meal: str
    ref: str
    food: dict
    measurement: int
    amount: float


@dataclass
class _Draft:
    day: str
    started: float
    items: list[_Item] = field(default_factory=list)
    next_n: int = 1


class Plans:
    def __init__(self, diary) -> None:
        self.diary = diary
        self._drafts: dict[str, _Draft] = {}

    async def _draft(self, token: str) -> tuple[str, _Draft]:
        account = await self.diary.sync.account(token)
        draft = self._drafts.get(account)
        if draft is None or time.monotonic() - draft.started > _TTL:
            self._drafts.pop(account, None)
            raise PlanError("No hay ningún borrador abierto: empieza uno con plan_start.")
        return account, draft

    async def start(self, token: str, when: Optional[str] = None) -> dict:
        account = await self.diary.sync.account(token)
        day = self.diary.parse_date(when)
        self._drafts[account] = _Draft(day=day.isoformat(), started=time.monotonic())
        return await self.show(token)

    async def add(self, token: str, meal: str, ref: str, amount: float, unit: str = "g") -> dict:
        _, draft = await self._draft(token)
        docs = await self.diary.sync.documents(token)
        meal_doc = self.diary._meal(docs, meal)
        food = self.diary._food(ref)
        measurement = self.diary._measurement(food, float(amount), unit)
        item = _Item(draft.next_n, meal_doc.id, str(meal_doc.value("name")), ref, food, measurement, float(amount))
        draft.items.append(item)
        draft.next_n += 1
        return {"added": {"item": item.n, "name": nutrition.food_name(food), "meal": item.meal}, **(await self.show(token))}

    async def remove(self, token: str, item: int) -> dict:
        _, draft = await self._draft(token)
        before = len(draft.items)
        draft.items = [i for i in draft.items if i.n != int(item)]
        if len(draft.items) == before:
            raise PlanError(f"No hay ningún item {item} en el borrador.")
        return await self.show(token)

    async def show(self, token: str) -> dict:
        """Lo que ya hay en el día más lo del borrador, frente a los objetivos."""
        _, draft = await self._draft(token)
        docs = await self.diary.sync.documents(token)
        day = self.diary.parse_date(draft.day)
        goals = self.diary._goals_of(docs, day)
        tracked = [nutrition.nutrient_column(f) for f in (goals or {}).get("tracked", [])]
        columns = tuple(dict.fromkeys(nutrition.BASIC + tuple(c[0] for c in tracked if c)))

        existing: dict = {}
        entries = self.diary._entries_of_day(docs, day)
        for e in entries:
            nutrition.add_vectors(existing, nutrition.entry_vector(e.kind, e.values(), columns))
        added: dict = {}
        items = []
        for i in draft.items:
            vector = nutrition.food_vector_per(i.food, i.measurement, i.amount, columns) or {}
            nutrition.add_vectors(added, vector)
            items.append({
                "item": i.n,
                "meal": i.meal,
                "name": nutrition.food_name(i.food),
                "amount": i.amount,
                "unit": nutrition.UNIT_NAMES.get(i.measurement, "g"),
                "kcal": round(vector.get("energy", (0.0, True))[0], 1),
            })
        total = nutrition.add_vectors(dict(existing), added)
        result = {
            "date": draft.day,
            "alreadyInDiary": {"entries": len(entries), **nutrition.present(existing, columns)},
            "draft": items,
            "total": nutrition.present(total, columns),
        }
        if goals is not None:
            targets = goals["targets"]
            result["goals"] = targets
            result["remaining"] = {
                "kcal": round(targets["kcal"] - total.get("energy", (0, True))[0], 1),
                **{m: round(targets[m] - total.get(m, (0, True))[0], 1) for m in ("proteins", "carbohydrates", "fats")},
            }
            tracked_out = []
            for f in goals["tracked"]:
                c = nutrition.nutrient_column(f)
                target = goals["values"].get(f)
                if not c or target is None:
                    continue
                amount, known = total.get(c[0], (0.0, True))
                tracked_out.append({
                    "nutrient": f, "unit": c[1], "target": round(float(target) * c[2], 1), "total": round(amount, 1),
                    "kind": "limit" if f in nutrition.LIMIT_FIELDS else "minimum", "complete": known,
                })
            if tracked_out:
                result["trackedNutrients"] = tracked_out
        return result

    async def commit(self, token: str, eaten: bool = False) -> dict:
        account, draft = await self._draft(token)
        if not draft.items:
            raise PlanError("El borrador está vacío: no hay nada que escribir.")
        result = await self.diary.add_foods(
            token,
            [
                {"meal": i.meal_id, "food": i.food, "amount": i.amount, "unit": nutrition.UNIT_NAMES.get(i.measurement, "g"),
                 "date": draft.day, "eaten": eaten}
                for i in draft.items
            ],
        )
        self._drafts.pop(account, None)
        return {"written": len(result["added"]), "date": draft.day, "added": result["added"]}

    async def discard(self, token: str) -> dict:
        account = await self.diary.sync.account(token)
        draft = self._drafts.pop(account, None)
        return {"discarded": len(draft.items) if draft else 0}
