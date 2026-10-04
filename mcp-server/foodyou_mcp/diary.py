"""Las operaciones del MCP sobre el diario, sin nada del protocolo MCP: así se prueban solas.

Mismas reglas que el asistente de la app:
- lo que se añade entra SIN marcar como comido (es una propuesta hasta que la persona lo
  marca) y con la marca de "lo añadió el asistente";
- cada cambio se puede deshacer;
- los alimentos salen de una búsqueda, nunca se inventan sus macros.
"""

from __future__ import annotations

import json
import time
import uuid
from datetime import date, datetime, timedelta
from typing import Any, Optional
from zoneinfo import ZoneInfo

from foodyou_mcp import nutrition
from foodyou_mcp.foods import FoodRefs, FoodServer, FoodServerRefused, describe, product_snapshot
from foodyou_mcp.journal import Journal
from foodyou_mcp.openfoodfacts import OpenFoodFacts
from foodyou_mcp.recipes import Memory, RecipeError, Recipes
from foodyou_mcp.sync_client import Document, SyncClient, change

MEAL, ENTRY, MANUAL = "meal", "food_entry", "manual_entry"
GOALS, GOALS_ID = "goals", "goals"
_WEEKDAYS = ("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
_EPOCH = date(1970, 1, 1)


class DiaryError(ValueError):
    """Algo que Claude tiene que corregir; el mensaje le dice cómo."""


class Diary:
    def __init__(
        self,
        sync: SyncClient,
        food_server: FoodServer,
        journal: Journal,
        timezone: str = "Europe/Madrid",
        off: Optional[OpenFoodFacts] = None,
    ) -> None:
        self.sync = sync
        self.food_server = food_server
        self.journal = journal
        self.refs = FoodRefs()
        self.tz = ZoneInfo(timezone)
        self.off = off
        self.recipes = Recipes(self)
        self.memory = Memory(self)

    # --- Fechas y comidas -------------------------------------------------------------

    def today(self) -> date:
        return datetime.now(self.tz).date()

    def parse_date(self, value: Optional[str]) -> date:
        text = (value or "").strip().lower()
        if text in ("", "today", "hoy"):
            return self.today()
        if text in ("yesterday", "ayer"):
            return self.today() - timedelta(days=1)
        if text in ("tomorrow", "mañana", "manana"):
            return self.today() + timedelta(days=1)
        try:
            return date.fromisoformat(text)
        except ValueError:
            raise DiaryError(f"Fecha no válida: {value!r}. Usa AAAA-MM-DD, 'today' o 'yesterday'.")

    @staticmethod
    def epoch_day(d: date) -> int:
        return (d - _EPOCH).days

    @staticmethod
    def from_epoch_day(days: int) -> date:
        return _EPOCH + timedelta(days=int(days))

    @staticmethod
    def _live(docs: dict, kind: str) -> list[Document]:
        return [d for (k, _), d in docs.items() if k == kind and not d.deleted]

    def _meals(self, docs: dict) -> list[Document]:
        return sorted(self._live(docs, MEAL), key=lambda d: (d.value("rank", 0), d.value("name", "")))

    def _meal(self, docs: dict, meal: str) -> Document:
        meals = self._meals(docs)
        for m in meals:
            if m.id == meal or str(m.value("name", "")).strip().lower() == meal.strip().lower():
                return m
        names = ", ".join(str(m.value("name")) for m in meals) or "ninguna"
        raise DiaryError(f"No hay ninguna comida '{meal}'. Las comidas son: {names}.")

    async def meals(self, token: str) -> list[dict]:
        docs = await self.sync.documents(token)
        return [
            {
                "id": m.id,
                "name": m.value("name"),
                "from": f"{m.value('fromHour', 0):02d}:{m.value('fromMinute', 0):02d}",
                "to": f"{m.value('toHour', 0):02d}:{m.value('toMinute', 0):02d}",
            }
            for m in self._meals(docs)
        ]

    # --- Leer ---------------------------------------------------------------------------

    def _entry_view(self, doc: Document) -> dict:
        v = doc.values()
        if doc.kind == ENTRY:
            n = nutrition.entry_nutrition(v) or {}
            food = v.get("food") or {}
            return {
                "id": doc.id,
                "type": "recipe" if "recipe" in food else "food",
                "name": nutrition.food_name(food),
                "amount": v.get("quantity"),
                "unit": nutrition.UNIT_NAMES.get(int(v.get("measurement", 0)), "g"),
                "grams": n.get("grams"),
                "kcal": n.get("energy"),
                "proteins": n.get("proteins"),
                "carbohydrates": n.get("carbohydrates"),
                "fats": n.get("fats"),
                "eaten": bool(v.get("isEaten")),
                "byAssistant": bool(v.get("createdByAssistant")),
            }
        return {
            "id": doc.id,
            "type": "quick",
            "name": v.get("name"),
            "kcal": v.get("energy"),
            "proteins": v.get("proteins"),
            "carbohydrates": v.get("carbohydrates"),
            "fats": v.get("fats"),
            "ingredients": [i.get("name") for i in v.get("ingredients") or []],
            "eaten": bool(v.get("isEaten")),
            "byAssistant": bool(v.get("createdByAssistant")),
        }

    def _entries_of_day(self, docs: dict, day: date) -> list[Document]:
        epoch = self.epoch_day(day)
        out = [d for d in self._live(docs, ENTRY) if d.value("epochDay") == epoch]
        out += [d for d in self._live(docs, MANUAL) if d.value("dateEpochDay") == epoch]
        return sorted(out, key=lambda d: (d.value("position", 0), d.id))

    @staticmethod
    def _sum(views: list[dict], only_eaten: bool = False) -> dict:
        totals = {"kcal": 0.0, "proteins": 0.0, "carbohydrates": 0.0, "fats": 0.0}
        for v in views:
            if only_eaten and not v["eaten"]:
                continue
            for k in totals:
                totals[k] += float(v.get(k) or 0)
        return {k: round(x, 1) for k, x in totals.items()}

    # --- Objetivos ------------------------------------------------------------------------

    def _goals_of(self, docs: dict, day: date) -> Optional[dict]:
        """Los objetivos del día (cada día de la semana puede tener los suyos), como los guarda
        la app: gramos, o reparto de la energía en porcentajes si isDistribution. None si la
        cuenta aún no tiene objetivos sincronizados (móviles con una versión anterior a 4.1)."""
        doc = docs.get((GOALS, GOALS_ID))
        if doc is None or doc.deleted:
            return None
        goal = (doc.value("days") or {}).get(_WEEKDAYS[day.weekday()]) or {}
        values = goal.get("map") or {}
        kcal = float(values.get("Energy") or 0)
        if goal.get("isDistribution"):
            macros = {
                "proteins": kcal * float(values.get("Proteins") or 0) / 4,
                "carbohydrates": kcal * float(values.get("Carbohydrates") or 0) / 4,
                "fats": kcal * float(values.get("Fats") or 0) / 9,
            }
        else:
            macros = {k: float(values.get(k.capitalize()) or 0) for k in ("proteins", "carbohydrates", "fats")}
        return {
            "targets": {"kcal": round(kcal, 1), **{k: round(v, 1) for k, v in macros.items()}},
            "values": values,
            "tracked": [str(f) for f in doc.value("tracked") or []],
        }

    @staticmethod
    def _tracked_progress(goals: dict, entries: list[Document]) -> list[dict]:
        """Cómo va cada nutriente que la persona quiere cumplir (el calcio...), en su unidad."""
        out = []
        for field in goals["tracked"]:
            column = nutrition.nutrient_column(field)
            target = goals["values"].get(field)
            if column is None or target is None:
                continue
            name, unit, scale = column
            total = eaten = 0.0
            complete = True
            for entry in entries:
                amount, known = nutrition.entry_nutrient(entry.kind, entry.values(), name)
                total += amount
                if entry.value("isEaten"):
                    eaten += amount
                complete = complete and known
            out.append(
                {
                    "nutrient": field,
                    "unit": unit,
                    "target": round(float(target) * scale, 1),
                    "total": round(total, 1),
                    "eaten": round(eaten, 1),
                    "kind": "limit" if field in nutrition.LIMIT_FIELDS else "minimum",
                    # Si algún alimento no dice cuánto tiene, el total real es mayor.
                    "complete": complete,
                }
            )
        return out

    async def goals(self, token: str, when: Optional[str]) -> dict:
        docs = await self.sync.documents(token)
        day = self.parse_date(when)
        goals = self._goals_of(docs, day)
        if goals is None:
            return {"date": day.isoformat(), "available": False,
                    "note": "Los objetivos aún no se han sincronizado desde la app (hace falta la versión 4.1 o posterior)."}
        tracked = []
        for field in goals["tracked"]:
            column = nutrition.nutrient_column(field)
            if column and goals["values"].get(field) is not None:
                tracked.append({
                    "nutrient": field,
                    "target": round(float(goals["values"][field]) * column[2], 1),
                    "unit": column[1],
                    "kind": "limit" if field in nutrition.LIMIT_FIELDS else "minimum",
                })
        return {"date": day.isoformat(), "available": True, "targets": goals["targets"], "trackedNutrients": tracked}

    async def day(self, token: str, when: Optional[str]) -> dict:
        docs = await self.sync.documents(token)
        day = self.parse_date(when)
        entries = self._entries_of_day(docs, day)
        meals = []
        all_views = []
        for meal in self._meals(docs):
            views = [self._entry_view(e) for e in entries if e.value("meal") == meal.id]
            all_views += views
            meals.append({"meal": meal.value("name"), "mealId": meal.id, "entries": views, "totals": self._sum(views)})
        result = {
            "date": day.isoformat(),
            "totals": self._sum(all_views),
            "eatenTotals": self._sum(all_views, only_eaten=True),
            "meals": meals,
        }
        goals = self._goals_of(docs, day)
        if goals is not None:
            result["goals"] = goals["targets"]
            if goals["tracked"]:
                result["trackedNutrients"] = self._tracked_progress(goals, entries)
        return result

    async def summary(self, token: str, start: Optional[str], end: Optional[str]) -> dict:
        docs = await self.sync.documents(token)
        first = self.parse_date(start)
        last = self.parse_date(end or start)
        if last < first:
            first, last = last, first
        if (last - first).days > 92:
            raise DiaryError("Como mucho 92 días por consulta.")
        days = []
        current = first
        while current <= last:
            entries = self._entries_of_day(docs, current)
            views = [self._entry_view(e) for e in entries]
            row = {"date": current.isoformat(), "entries": len(views), **self._sum(views)}
            goals = self._goals_of(docs, current)
            if goals is not None and goals["tracked"]:
                row["tracked"] = {
                    t["nutrient"]: {"total": t["total"], "target": t["target"], "unit": t["unit"], "complete": t["complete"]}
                    for t in self._tracked_progress(goals, entries)
                }
            days.append(row)
            current += timedelta(days=1)
        logged = [d for d in days if d["entries"]]
        average = (
            {k: round(sum(d[k] for d in logged) / len(logged), 1) for k in ("kcal", "proteins", "carbohydrates", "fats")}
            if logged
            else None
        )
        return {"from": first.isoformat(), "to": last.isoformat(), "days": days, "averageOfLoggedDays": average}

    # --- Buscar -------------------------------------------------------------------------

    async def search(self, token: str, query: str, limit: int = 8, open_food_facts: bool = False) -> dict:
        """Por orden: lo que la persona ya ha comido (el producto exacto de siempre), sus
        recetas, la base de datos propia y, si se pide o no hay nada, Open Food Facts."""
        docs = await self.sync.documents(token)
        limit = max(1, min(limit, 20))
        needle = query.strip().lower()
        if not needle:
            raise DiaryError("Escribe qué buscar.")

        history, seen = [], set()
        for doc in sorted(self._live(docs, ENTRY), key=lambda d: -d.seq):
            food = doc.value("food")
            if not isinstance(food, dict):
                continue
            name = nutrition.food_name(food)
            if needle in name.lower() and name.lower() not in seen:
                seen.add(name.lower())
                history.append(describe(food, self.refs.remember(food), "history"))
            if len(history) >= limit:
                break

        recipes = []
        for food, recipe in self.recipes.search_hits(docs, needle, limit):
            hit = describe(food, self.refs.remember(food), "recipe")
            hit["recipeId"] = recipe.id
            recipes.append(hit)

        result: dict = {"history": history, "recipes": recipes, "database": []}
        if self.food_server.configured:
            on_behalf = await self.sync.food_account(token)
            try:
                products = await self.food_server.search(query, limit, on_behalf=on_behalf)
                result["database"] = [
                    describe(food, self.refs.remember(food), "database")
                    for food in (product_snapshot(p) for p in products)
                ]
            except FoodServerRefused as exc:
                # Lo demás sigue valiendo; a Claude se le dice por qué no hay más.
                result["databaseUnavailable"] = str(exc)

        if self.off is not None and (open_food_facts or not result["database"]):
            try:
                result["openFoodFacts"] = [
                    describe(food, self.refs.remember(food), "openfoodfacts")
                    for food in await self.off.search(query, limit)
                ]
            except Exception:  # noqa: BLE001 - un catálogo externo caído no tumba la búsqueda
                result["openFoodFactsUnavailable"] = "Open Food Facts no responde ahora."
        return result

    # --- Escribir -----------------------------------------------------------------------

    def _next_position(self, docs: dict, meal_id: str, epoch: int) -> int:
        positions = [
            int(d.value("position", 0))
            for d in self._live(docs, ENTRY)
            if d.value("meal") == meal_id and d.value("epochDay") == epoch
        ] + [
            int(d.value("position", 0))
            for d in self._live(docs, MANUAL)
            if d.value("meal") == meal_id and d.value("dateEpochDay") == epoch
        ]
        return max(positions, default=-1) + 1

    def _measurement(self, food: dict, amount: float, unit: str) -> int:
        if amount <= 0:
            raise DiaryError("La cantidad tiene que ser mayor que 0.")
        measurement = nutrition.UNITS.get((unit or "g").strip().lower())
        if measurement is None:
            raise DiaryError(f"Unidad no válida: {unit}. Usa g, ml, serving o package.")
        if nutrition.weight_of(food, measurement, amount) is None:
            raise DiaryError("Ese alimento no tiene peso de ración o de envase: indica la cantidad en g o ml.")
        return measurement

    def _food(self, ref: str) -> dict:
        food = self.refs.get(ref)
        if food is None:
            raise DiaryError(f"La referencia '{ref}' no existe o caducó: vuelve a buscar el alimento.")
        return food

    def _entry_values(self, docs: dict, meal_doc: Document, epoch: int, food: dict, measurement: int,
                      amount: float, eaten: bool, position: int) -> dict:
        now = int(time.time())
        return {
            "meal": meal_doc.id,
            "epochDay": epoch,
            "measurement": measurement,
            "quantity": float(amount),
            "isEaten": 1 if eaten else 0,
            "createdAt": now,
            "updatedAt": now,
            "position": position,
            "createdByAssistant": 1,
            "food": food,
            "_deleted": False,
        }

    async def _commit(self, token: str, changes: list[dict], summary: str, undo: list[dict], redo: list[dict]) -> dict:
        """Escribe varios cambios a la vez y los deja como un único paso de deshacer."""
        docs = await self.sync.push(token, changes)
        account = await self.sync.account(token)
        self.journal.record(account, summary, undo, redo)
        return docs

    async def add_food(
        self, token: str, when: Optional[str], meal: str, ref: str, amount: float, unit: str = "g", eaten: bool = False
    ) -> dict:
        result = await self.add_foods(
            token, [{"meal": meal, "ref": ref, "amount": amount, "unit": unit, "date": when, "eaten": eaten}]
        )
        added = result["added"][0]
        return {"added": added, "date": added.pop("date"), "meal": added.pop("meal")}

    async def add_foods(self, token: str, items: list[dict]) -> dict:
        """Varios alimentos de una vez (un día entero, una receta en el diario...): una sola
        escritura y un solo paso de deshacer. Cada item: meal, ref, amount, unit, date, eaten."""
        if not items:
            raise DiaryError("No hay nada que añadir.")
        if len(items) > 40:
            raise DiaryError("Como mucho 40 alimentos por llamada.")
        docs = await self.sync.documents(token)
        changes, undo, redo, plan = [], [], [], []
        next_position: dict[tuple[str, int], int] = {}
        for n, item in enumerate(items, start=1):
            try:
                # El borrador pasa el alimento entero: sus refs pueden haber caducado ya.
                food = item["food"] if isinstance(item.get("food"), dict) else self._food(str(item.get("ref", "")))
                amount = float(item.get("amount") or 0)
                measurement = self._measurement(food, amount, str(item.get("unit") or "g"))
                meal_doc = self._meal(docs, str(item.get("meal", "")))
                day = self.parse_date(item.get("date"))
            except DiaryError as exc:
                raise DiaryError(f"Alimento {n}: {exc}") from None
            epoch = self.epoch_day(day)
            key = (meal_doc.id, epoch)
            position = next_position.get(key, self._next_position(docs, meal_doc.id, epoch))
            next_position[key] = position + 1
            entry_id = uuid.uuid4().hex
            values = self._entry_values(docs, meal_doc, epoch, food, measurement, amount, bool(item.get("eaten")), position)
            changes.append(change(ENTRY, entry_id, values))
            undo.append({"kind": ENTRY, "id": entry_id, "values": {"_deleted": True}})
            redo.append({"kind": ENTRY, "id": entry_id, "values": {"_deleted": False}})
            plan.append((entry_id, day, meal_doc))
        names = [nutrition.food_name(c["fields"]["food"]["value"]) for c in changes]
        summary = (
            f"Añadido {names[0]} a {plan[0][2].value('name')} ({plan[0][1].isoformat()})"
            if len(names) == 1
            else f"Añadidos {len(names)} alimentos: {', '.join(names[:5])}{'...' if len(names) > 5 else ''}"
        )
        docs = await self._commit(token, changes, summary, undo, redo)
        return {
            "added": [
                {**self._entry_view(docs[(ENTRY, entry_id)]), "date": day.isoformat(), "meal": meal_doc.value("name")}
                for entry_id, day, meal_doc in plan
            ]
        }

    async def add_quick(
        self,
        token: str,
        when: Optional[str],
        meal: str,
        name: str,
        kcal: float,
        proteins: Optional[float],
        carbohydrates: Optional[float],
        fats: Optional[float],
        ingredients: Optional[list[str]] = None,
        eaten: bool = False,
    ) -> dict:
        if not name.strip():
            raise DiaryError("Ponle un nombre.")
        if kcal < 0:
            raise DiaryError("Las kcal no pueden ser negativas.")
        docs = await self.sync.documents(token)
        meal_doc = self._meal(docs, meal)
        day = self.parse_date(when)
        epoch = self.epoch_day(day)
        now = int(time.time())
        entry_id = uuid.uuid4().hex
        values = {
            "meal": meal_doc.id,
            "dateEpochDay": epoch,
            "name": name.strip(),
            "energy": float(kcal),
            "proteins": proteins,
            "carbohydrates": carbohydrates,
            "fats": fats,
            "createdEpochSeconds": now,
            "updatedEpochSeconds": now,
            "position": self._next_position(docs, meal_doc.id, epoch),
            "isEaten": 1 if eaten else 0,
            "createdByAssistant": 1,
            "ingredients": [
                {"name": str(i), "grams": None, "position": n} for n, i in enumerate(ingredients or [])
            ],
            "_deleted": False,
        }
        docs = await self._commit(
            token,
            [change(MANUAL, entry_id, values)],
            f"Añadida entrada rápida {name.strip()} a {meal_doc.value('name')} ({day.isoformat()})",
            [{"kind": MANUAL, "id": entry_id, "values": {"_deleted": True}}],
            [{"kind": MANUAL, "id": entry_id, "values": {"_deleted": False}}],
        )
        return {"added": self._entry_view(docs[(MANUAL, entry_id)]), "date": day.isoformat()}

    def _find_entry(self, docs: dict, entry_id: str) -> Document:
        for kind in (ENTRY, MANUAL):
            doc = docs.get((kind, entry_id))
            if doc is not None and not doc.deleted:
                return doc
        raise DiaryError(f"No hay ninguna entrada con id {entry_id}. Consulta el día con get_day.")

    async def update_entry(
        self,
        token: str,
        entry_id: str,
        amount: Optional[float] = None,
        unit: Optional[str] = None,
        eaten: Optional[bool] = None,
        meal: Optional[str] = None,
        when: Optional[str] = None,
    ) -> dict:
        docs = await self.sync.documents(token)
        doc = self._find_entry(docs, entry_id)
        values: dict[str, Any] = {}
        if eaten is not None:
            values["isEaten"] = 1 if eaten else 0
        if meal is not None:
            values["meal"] = self._meal(docs, meal).id
        if when is not None:
            values["epochDay" if doc.kind == ENTRY else "dateEpochDay"] = self.epoch_day(self.parse_date(when))
        if amount is not None or unit is not None:
            if doc.kind != ENTRY:
                raise DiaryError("Una entrada rápida no tiene cantidad: bórrala y añádela de nuevo.")
            measurement = (
                nutrition.UNITS.get(unit.strip().lower()) if unit else int(doc.value("measurement", 0))
            )
            if measurement is None:
                raise DiaryError(f"Unidad no válida: {unit}.")
            quantity = float(amount) if amount is not None else float(doc.value("quantity"))
            if quantity <= 0:
                raise DiaryError("La cantidad tiene que ser mayor que 0.")
            if nutrition.weight_of(doc.value("food") or {}, measurement, quantity) is None:
                raise DiaryError("Ese alimento no tiene peso de ración o de envase: usa g o ml.")
            values["measurement"] = measurement
            values["quantity"] = quantity
        if not values:
            raise DiaryError("No has dicho qué cambiar.")
        stamp = "updatedAt" if doc.kind == ENTRY else "updatedEpochSeconds"
        values[stamp] = int(time.time())

        before = {k: doc.value(k) for k in values}
        docs = await self._commit(
            token,
            [change(doc.kind, doc.id, values, doc)],
            f"Cambiada {self._entry_view(doc)['name']}",
            [{"kind": doc.kind, "id": doc.id, "values": before}],
            [{"kind": doc.kind, "id": doc.id, "values": values}],
        )
        return {"updated": self._entry_view(docs[(doc.kind, doc.id)])}

    async def delete_entry(self, token: str, entry_id: str) -> dict:
        result = await self.delete_entries(token, [entry_id])
        return {"deleted": result["deleted"][0]}

    async def delete_entries(self, token: str, entry_ids: list[str]) -> dict:
        if not entry_ids:
            raise DiaryError("Di qué entradas borrar.")
        docs = await self.sync.documents(token)
        targets = [self._find_entry(docs, e) for e in dict.fromkeys(entry_ids)]
        views = [self._entry_view(d) for d in targets]
        await self._commit(
            token,
            [change(d.kind, d.id, {"_deleted": True}, d) for d in targets],
            f"Borrada {views[0]['name']}" if len(views) == 1 else f"Borradas {len(views)} entradas",
            [{"kind": d.kind, "id": d.id, "values": {"_deleted": False}} for d in targets],
            [{"kind": d.kind, "id": d.id, "values": {"_deleted": True}} for d in targets],
        )
        return {"deleted": views}

    async def set_eaten(self, token: str, entry_ids: list[str], eaten: bool = True) -> dict:
        """Marca o desmarca varias entradas como comidas de una vez."""
        if not entry_ids:
            raise DiaryError("Di qué entradas marcar.")
        docs = await self.sync.documents(token)
        targets = [self._find_entry(docs, e) for e in dict.fromkeys(entry_ids)]
        value = 1 if eaten else 0
        now = int(time.time())

        def stamp(d: Document) -> str:
            return "updatedAt" if d.kind == ENTRY else "updatedEpochSeconds"

        docs = await self._commit(
            token,
            [change(d.kind, d.id, {"isEaten": value, stamp(d): now}, d) for d in targets],
            f"{'Marcadas' if eaten else 'Desmarcadas'} {len(targets)} entradas como comidas",
            [{"kind": d.kind, "id": d.id, "values": {"isEaten": d.value("isEaten")}} for d in targets],
            [{"kind": d.kind, "id": d.id, "values": {"isEaten": value}} for d in targets],
        )
        return {"updated": [self._entry_view(docs[(d.kind, d.id)]) for d in targets]}

    async def _replay(self, token: str, items: list[dict]) -> None:
        docs = await self.sync.documents(token)
        await self.sync.push(
            token, [change(item["kind"], item["id"], item["values"], docs.get((item["kind"], item["id"]))) for item in items]
        )

    async def undo(self, token: str) -> dict:
        account = await self.sync.account(token)
        last = self.journal.last(account)
        if last is None:
            raise DiaryError("No hay nada que deshacer.")
        await self._replay(token, json.loads(last["undo"]))
        self.journal.mark_undone(last["id"])
        return {"undone": last["summary"]}

    async def redo(self, token: str) -> dict:
        account = await self.sync.account(token)
        entry = self.journal.last_undone(account)
        if entry is None:
            raise DiaryError("No hay nada que rehacer.")
        await self._replay(token, json.loads(entry["redo"]))
        self.journal.mark_redone(entry["id"])
        return {"redone": entry["summary"]}

    async def history(self, token: str) -> list[dict]:
        account = await self.sync.account(token)
        return [
            {"summary": r["summary"], "at": datetime.fromtimestamp(r["at"], self.tz).isoformat(timespec="minutes"), "undone": bool(r["undone"])}
            for r in self.journal.recent(account)
        ]
