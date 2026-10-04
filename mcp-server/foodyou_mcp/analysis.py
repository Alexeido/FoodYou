"""Lo que se puede saber mirando el diario: totales con todos los nutrientes, qué se come más,
de dónde sale cada nutriente, a qué hora se apunta cada comida y cuándo se comió algo.

Lo mismo que tiene el asistente de la app (dailyTotals, topFoods, topBrands,
nutrientAttribution, mealTimingStats, searchDiary), calculado sobre el diario sincronizado.
"""

from __future__ import annotations

import re
from collections import defaultdict
from datetime import date, datetime, timedelta
from typing import Optional

from foodyou_mcp import nutrition
from foodyou_mcp.sync_client import Document

ENTRY, MANUAL = "food_entry", "manual_entry"
MAX_DAYS = 366
_BRAND = re.compile(r"\(([^()]+)\)\s*$")
_WEEKDAYS = ("lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo")


class AnalysisError(ValueError):
    pass


def _columns(detail: Optional[str]) -> tuple[str, ...]:
    level = (detail or "basic").strip().lower()
    if level == "basic":
        return nutrition.BASIC
    if level == "extended":
        return nutrition.EXTENDED
    if level == "full":
        return nutrition.FULL
    raise AnalysisError("detail: basic, extended (fibra, azúcar, sal...) o full (vitaminas y minerales).")


class Analysis:
    def __init__(self, diary) -> None:
        self.diary = diary

    # --- Utilidades ---------------------------------------------------------------------

    def _range(self, start: Optional[str], end: Optional[str]) -> tuple[date, date]:
        first = self.diary.parse_date(start)
        last = self.diary.parse_date(end or start)
        if last < first:
            first, last = last, first
        if (last - first).days >= MAX_DAYS:
            raise AnalysisError(f"Como mucho {MAX_DAYS} días por consulta.")
        return first, last

    def _entries(self, docs: dict, first: date, last: date) -> list[tuple[date, Document]]:
        lo, hi = self.diary.epoch_day(first), self.diary.epoch_day(last)
        out = []
        for d in self.diary._live(docs, ENTRY):
            epoch = d.value("epochDay")
            if isinstance(epoch, int) and lo <= epoch <= hi:
                out.append((self.diary.from_epoch_day(epoch), d))
        for d in self.diary._live(docs, MANUAL):
            epoch = d.value("dateEpochDay")
            if isinstance(epoch, int) and lo <= epoch <= hi:
                out.append((self.diary.from_epoch_day(epoch), d))
        return out

    @staticmethod
    def _name(doc: Document) -> str:
        if doc.kind == MANUAL:
            return str(doc.value("name") or "?")
        return nutrition.food_name(doc.value("food") or {})

    @staticmethod
    def _brand(doc: Document) -> Optional[str]:
        food = doc.value("food") or {}
        product = food.get("product") if isinstance(food, dict) else None
        if isinstance(product, dict) and product.get("brand"):
            return str(product["brand"])
        match = _BRAND.search(Analysis._name(doc))
        return match.group(1).strip() if match else None

    def _meal_names(self, docs: dict) -> dict[str, str]:
        return {m.id: str(m.value("name")) for m in self.diary._meals(docs)}

    # --- Totales ------------------------------------------------------------------------

    async def totals(
        self,
        token: str,
        start: str,
        end: Optional[str] = None,
        group_by: str = "day",
        only_eaten: bool = False,
        detail: str = "basic",
    ) -> dict:
        """Totales entre dos fechas agrupados por día, día de la semana, semana, mes o comida.
        Los días sin nada también salen (con ceros), como en la app."""
        columns = _columns(detail)
        docs = await self.diary.sync.documents(token)
        first, last = self._range(start, end)
        meals = self._meal_names(docs)

        def key_of(day: date, doc: Document) -> str:
            if group_by == "day":
                return day.isoformat()
            if group_by == "weekday":
                return _WEEKDAYS[day.weekday()]
            if group_by == "week":
                monday = day - timedelta(days=day.weekday())
                return f"semana del {monday.isoformat()}"
            if group_by == "month":
                return day.strftime("%Y-%m")
            if group_by == "meal":
                return meals.get(str(doc.value("meal")), "?")
            raise AnalysisError("group_by: day, weekday, week, month o meal.")

        groups: dict[str, dict] = {}
        counts: dict[str, int] = defaultdict(int)
        days_in: dict[str, set] = defaultdict(set)
        if group_by == "day":
            current = first
            while current <= last:
                groups[current.isoformat()] = {}
                current += timedelta(days=1)
        for day, doc in sorted(self._entries(docs, first, last), key=lambda x: x[0]):
            if only_eaten and not doc.value("isEaten"):
                continue
            key = key_of(day, doc)
            nutrition.add_vectors(groups.setdefault(key, {}), nutrition.entry_vector(doc.kind, doc.values(), columns))
            counts[key] += 1
            days_in[key].add(day)

        rows = []
        for key, vector in groups.items():
            row = {"group": key, "entries": counts.get(key, 0), **nutrition.present(vector, columns)}
            if group_by in ("weekday", "week", "month"):
                # La media por día con algo apuntado: comparar semanas de distinto largo.
                n = len(days_in[key]) or 1
                row["daysLogged"] = n
                row["averagePerDay"] = {
                    k: round(v / n, 1) if isinstance(v, (int, float)) else {**v, "value": round(v["value"] / n, 1)}
                    for k, v in nutrition.present(vector, columns).items()
                }
            rows.append(row)
        if group_by == "weekday":
            rows.sort(key=lambda r: _WEEKDAYS.index(r["group"]))
        return {
            "from": first.isoformat(),
            "to": last.isoformat(),
            "groupBy": group_by,
            "onlyEaten": only_eaten,
            "groups": rows,
            "note": "complete=false: algún alimento no trae ese dato; el total real es mayor.",
        }

    # --- Lo que más se come -------------------------------------------------------------

    async def top_foods(self, token: str, start: str, end: Optional[str] = None, limit: int = 15) -> dict:
        docs = await self.diary.sync.documents(token)
        first, last = self._range(start, end)
        stats: dict[str, dict] = {}
        for day, doc in self._entries(docs, first, last):
            name = self._name(doc)
            vector = nutrition.entry_vector(doc.kind, doc.values(), nutrition.BASIC)
            s = stats.setdefault(name, {"name": name, "times": 0, "kcal": 0.0, "grams": 0.0, "days": set(), "last": day})
            s["times"] += 1
            s["kcal"] += vector["energy"][0]
            if doc.kind == ENTRY:
                n = nutrition.entry_nutrition(doc.values()) or {}
                s["grams"] += float(n.get("grams") or 0)
            s["days"].add(day)
            s["last"] = max(s["last"], day)
        top = sorted(stats.values(), key=lambda s: (-s["times"], -s["kcal"]))[: max(1, min(limit, 50))]
        return {
            "from": first.isoformat(),
            "to": last.isoformat(),
            "foods": [
                {
                    "name": s["name"],
                    "times": s["times"],
                    "days": len(s["days"]),
                    "lastTime": s["last"].isoformat(),
                    "totalKcal": round(s["kcal"], 1),
                    "averageGrams": round(s["grams"] / s["times"], 1) if s["grams"] else None,
                }
                for s in top
            ],
        }

    async def top_brands(self, token: str, start: str, end: Optional[str] = None, limit: int = 10) -> dict:
        docs = await self.diary.sync.documents(token)
        first, last = self._range(start, end)
        brands: dict[str, dict] = {}
        for _, doc in self._entries(docs, first, last):
            brand = self._brand(doc)
            if not brand:
                continue
            b = brands.setdefault(brand.lower(), {"brand": brand, "times": 0, "foods": set()})
            b["times"] += 1
            b["foods"].add(self._name(doc))
        top = sorted(brands.values(), key=lambda b: -b["times"])[: max(1, min(limit, 30))]
        return {
            "from": first.isoformat(),
            "to": last.isoformat(),
            "brands": [{"brand": b["brand"], "times": b["times"], "foods": sorted(b["foods"])[:8]} for b in top],
        }

    # --- De dónde sale un nutriente -----------------------------------------------------

    async def nutrient_sources(
        self, token: str, nutrient: str, start: str, end: Optional[str] = None, limit: int = 10, only_eaten: bool = False
    ) -> dict:
        column = nutrition.column_for(nutrient)
        if column is None:
            raise AnalysisError(f"No conozco el nutriente '{nutrient}'. Prueba con kcal, proteins, fats, Calcium, DietaryFiber...")
        field, unit = nutrition.COLUMNS[column]
        docs = await self.diary.sync.documents(token)
        first, last = self._range(start, end)
        per_food: dict[str, float] = defaultdict(float)
        unknown: set[str] = set()
        total = 0.0
        for _, doc in self._entries(docs, first, last):
            if only_eaten and not doc.value("isEaten"):
                continue
            amount, known = nutrition.entry_vector(doc.kind, doc.values(), (column,))[column]
            name = self._name(doc)
            if not known:
                unknown.add(name)
            per_food[name] += amount
            total += amount
        top = sorted(per_food.items(), key=lambda x: -x[1])[: max(1, min(limit, 50))]
        return {
            "nutrient": field,
            "unit": unit,
            "from": first.isoformat(),
            "to": last.isoformat(),
            "total": round(total, 1),
            "sources": [
                {"name": name, "amount": round(amount, 1), "share": round(100 * amount / total, 1) if total else 0.0}
                for name, amount in top
                if amount > 0
            ],
            # Lo que no dice cuánto tiene: podría aportar y no se sabe.
            "withoutData": sorted(unknown)[:20],
        }

    # --- Horas y búsqueda en el historial -----------------------------------------------

    async def meal_times(self, token: str, start: str, end: Optional[str] = None) -> dict:
        """A qué hora se suele APUNTAR cada comida (no a qué hora se come)."""
        docs = await self.diary.sync.documents(token)
        first, last = self._range(start, end)
        meals = self._meal_names(docs)
        minutes: dict[str, list[int]] = defaultdict(list)
        for _, doc in self._entries(docs, first, last):
            stamp = doc.value("createdAt") if doc.kind == ENTRY else doc.value("createdEpochSeconds")
            if not isinstance(stamp, (int, float)) or stamp <= 0:
                continue
            # La app guardaba milisegundos en algunas versiones.
            seconds = stamp / 1000 if stamp > 10**11 else stamp
            local = datetime.fromtimestamp(seconds, self.diary.tz)
            minutes[meals.get(str(doc.value("meal")), "?")].append(local.hour * 60 + local.minute)
        out = []
        for meal, values in minutes.items():
            values.sort()
            median = values[len(values) // 2]
            out.append({
                "meal": meal,
                "entries": len(values),
                "usualTime": f"{median // 60:02d}:{median % 60:02d}",
                "earliest": f"{values[0] // 60:02d}:{values[0] % 60:02d}",
                "latest": f"{values[-1] // 60:02d}:{values[-1] % 60:02d}",
            })
        return {
            "from": first.isoformat(),
            "to": last.isoformat(),
            "meals": sorted(out, key=lambda m: m["usualTime"]),
            "note": "Es la hora a la que se apuntó, no necesariamente a la que se comió.",
        }

    async def find(self, token: str, query: str, limit: int = 15) -> dict:
        """Cuándo se comió algo: las entradas cuyo nombre lo contiene, de la más reciente."""
        needle = query.strip().lower()
        if not needle:
            raise AnalysisError("Escribe qué buscar.")
        docs = await self.diary.sync.documents(token)
        meals = self._meal_names(docs)
        hits = []
        for doc in self.diary._live(docs, ENTRY) + self.diary._live(docs, MANUAL):
            name = self._name(doc)
            if needle not in name.lower():
                continue
            epoch = doc.value("epochDay") if doc.kind == ENTRY else doc.value("dateEpochDay")
            if not isinstance(epoch, int):
                continue
            view = self.diary._entry_view(doc)
            hits.append({
                "date": self.diary.from_epoch_day(epoch).isoformat(),
                "meal": meals.get(str(doc.value("meal")), "?"),
                "name": name,
                "amount": view.get("amount"),
                "unit": view.get("unit"),
                "kcal": view.get("kcal"),
                "eaten": view.get("eaten"),
                "id": doc.id,
            })
        hits.sort(key=lambda h: h["date"], reverse=True)
        return {"query": query, "found": len(hits), "entries": hits[: max(1, min(limit, 100))]}
