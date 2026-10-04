"""El servidor MCP de Food You: el diario de la cuenta, para leer y escribir desde Claude.

Desde claude.ai (web y móvil) se añade como conector personalizado con la URL
https://sync.alexeido.com/mcp: pide iniciar sesión con la cuenta de sincronización (OAuth, ver
sync-server/app/oauth.py). Desde Claude Code también vale un token del panel:

    claude mcp add --transport http foodyou https://sync.alexeido.com/mcp \\
        --header "Authorization: Bearer fy_mcp_..."

El MCP no guarda contraseñas ni decide permisos: reenvía el token al servidor de
sincronización en cada petición, y si allí se quita el acceso, deja de funcionar al momento.
"""

from __future__ import annotations

import hashlib
import logging
import time
from typing import Optional

import httpx

import uvicorn
from mcp.server.mcpserver import Context, MCPServer
from mcp.server.mcpserver.exceptions import ToolError
from mcp.server.transport_security import TransportSecuritySettings
from starlette.requests import Request
from starlette.responses import JSONResponse

from foodyou_mcp.config import Config, load
from foodyou_mcp.analysis import Analysis, AnalysisError
from foodyou_mcp.diary import Diary, DiaryError
from foodyou_mcp.foods import FoodServer
from foodyou_mcp.journal import Journal
from foodyou_mcp.openfoodfacts import OpenFoodFacts
from foodyou_mcp.plan import PlanError, Plans
from foodyou_mcp.recipes import RecipeError
from foodyou_mcp.sync_client import AccessDenied, SyncClient

log = logging.getLogger("foodyou_mcp")

INSTRUCTIONS = """Diario de comidas de Food You de la persona: lo que ha comido o piensa comer,
día a día, repartido en sus comidas (desayuno, comida...). Los cambios llegan a su móvil al
momento. Es lo mismo que puede hacer el asistente de la app, y comparte con él las recetas y la
memoria.

Al empezar, mira get_memory: es lo que la persona ha contado de sí misma (alergias, gustos,
objetivos). Si cuenta algo que valga para otras veces, guárdalo con remember.

Reglas:
- Los alimentos salen SIEMPRE de search_food (historial, sus recetas, la base de datos propia y
  Open Food Facts). Nunca inventes macros. Si no aparece, add_quick_entry con una estimación, y
  dilo.
- Lo que añades entra SIN marcar como comido, salvo que la persona diga que ya se lo ha comido.
- Las fechas son AAAA-MM-DD, o 'today'/'yesterday'. Las comidas se nombran como en get_meals.
- Para varios alimentos usa add_foods (una sola llamada, un solo deshacer). Para planificar un día
  entero, usa el borrador: plan_start, plan_add, mira plan_show (suma contra los objetivos) y
  plan_commit cuando cuadre, o plan_discard.
- undo_last deshace tu último cambio y redo lo rehace. Antes de cambiar o borrar, get_day da los
  ids.
- get_day trae los objetivos del día y, en trackedNutrients, los nutrientes que la persona ha
  elegido cumplir (por ejemplo el calcio): son tan importantes para ella como las kcal. Cuando
  resumas un día o propongas comida, di cómo va con ellos y prioriza alimentos que ayuden. Un
  "minimum" hay que alcanzarlo; un "limit" no hay que pasarlo. Si complete es false, algún
  alimento no trae ese dato y el total real es mayor.
- Para proponer comida, mira antes top_foods: acertar con lo que ya come vale más que inventar.
- get_totals con detail extended/full da fibra, azúcar, sal, vitaminas y minerales; pídelo solo
  cuando haga falta. nutrient_sources dice de dónde sale un nutriente."""


def build(config: Optional[Config] = None, *, sync: Optional[SyncClient] = None, food: Optional[FoodServer] = None,
          off: Optional[OpenFoodFacts] = None) -> tuple[MCPServer, Diary]:
    config = config or load()
    diary = Diary(
        sync or SyncClient(config.sync_url),
        food or FoodServer(config.food_url, config.food_user, config.food_password),
        Journal(config.data_dir / "mcp.db"),
        config.timezone,
        off=off if off is not None else (OpenFoodFacts() if config.open_food_facts else None),
    )
    analysis = Analysis(diary)
    plans = Plans(diary)
    server = MCPServer(
        "Food You",
        instructions=INSTRUCTIONS,
        website_url="https://foods.alexeido.com",
    )

    def token_of(ctx: Context) -> str:
        auth = (ctx.headers or {}).get("authorization") or ""
        if not auth.lower().startswith("bearer "):
            raise AccessDenied("Falta el token: conecta el MCP con --header \"Authorization: Bearer ...\".")
        return auth[7:].strip()

    async def run(ctx: Context, operation):
        try:
            return await operation(token_of(ctx))
        except (DiaryError, RecipeError, AnalysisError, PlanError, AccessDenied) as exc:
            # El texto del error le llega a Claude tal cual: le dice qué corregir.
            raise ToolError(str(exc)) from None

    # --- Leer el diario --------------------------------------------------------------------

    @server.tool(description="Las comidas del día configuradas en la app (desayuno, comida...), con su horario.")
    async def get_meals(ctx: Context) -> list[dict]:
        return await run(ctx, diary.meals)

    @server.tool(
        description=(
            "El diario de un día: cada comida con sus entradas (id, nombre, cantidad, kcal, "
            "proteínas, carbohidratos, grasas, si ya se ha comido), los totales del día (de todo y "
            "de solo lo comido), los objetivos y cómo van los nutrientes que la persona quiere cumplir."
        )
    )
    async def get_day(ctx: Context, date: str = "today") -> dict:
        return await run(ctx, lambda t: diary.day(t, date))

    @server.tool(
        description=(
            "Los objetivos de un día: kcal y macros, y los nutrientes que la persona quiere "
            "cumplir (trackedNutrients, con su unidad y si son un mínimo o un límite)."
        )
    )
    async def get_goals(ctx: Context, date: str = "today") -> dict:
        return await run(ctx, lambda t: diary.goals(t, date))

    @server.tool(
        description=(
            "Resumen rápido por día entre dos fechas (máximo 92 días): kcal y macros, los "
            "nutrientes que la persona quiere cumplir y la media de los días con algo apuntado."
        )
    )
    async def get_summary(ctx: Context, from_date: str, to_date: str = "today") -> dict:
        return await run(ctx, lambda t: diary.summary(t, from_date, to_date))

    @server.tool(
        description=(
            "Totales entre dos fechas (hasta un año) agrupados por day, weekday, week, month o "
            "meal. detail: basic (kcal y macros), extended (+ grasa saturada, azúcar, fibra, sal, "
            "colesterol, cafeína) o full (+ todas las vitaminas y minerales). only_eaten cuenta "
            "solo lo marcado como comido. Los días sin nada salen con ceros."
        )
    )
    async def get_totals(
        ctx: Context, from_date: str, to_date: str = "today", group_by: str = "day", only_eaten: bool = False,
        detail: str = "basic",
    ) -> dict:
        return await run(ctx, lambda t: analysis.totals(t, from_date, to_date, group_by, only_eaten, detail))

    @server.tool(
        description=(
            "Los alimentos que más se repiten en el diario en un rango (veces, días, última vez, "
            "kcal y cantidad media). Úsalo antes de proponer comida."
        )
    )
    async def top_foods(ctx: Context, from_date: str, to_date: str = "today", limit: int = 15) -> dict:
        return await run(ctx, lambda t: analysis.top_foods(t, from_date, to_date, limit))

    @server.tool(description="Las marcas que más aparecen en el diario: dónde compra la persona.")
    async def top_brands(ctx: Context, from_date: str, to_date: str = "today", limit: int = 10) -> dict:
        return await run(ctx, lambda t: analysis.top_brands(t, from_date, to_date, limit))

    @server.tool(
        description=(
            "De qué alimentos sale un nutriente en un rango, de más a menos (calcio, grasa, "
            "fibra, azúcar, kcal...). Para responder de dónde viene algo o qué recortar."
        )
    )
    async def nutrient_sources(
        ctx: Context, nutrient: str, from_date: str, to_date: str = "today", limit: int = 10, only_eaten: bool = False
    ) -> dict:
        return await run(ctx, lambda t: analysis.nutrient_sources(t, nutrient, from_date, to_date, limit, only_eaten))

    @server.tool(
        description=(
            "A qué hora se suele APUNTAR cada comida (no necesariamente a la que se come; si lo "
            "usas, dilo)."
        )
    )
    async def meal_times(ctx: Context, from_date: str, to_date: str = "today") -> dict:
        return await run(ctx, lambda t: analysis.meal_times(t, from_date, to_date))

    @server.tool(
        description=(
            "Busca en el diario ya apuntado: cuándo se comió algo por última vez, cuántas veces... "
            "No confundir con search_food, que busca alimentos para añadir."
        )
    )
    async def find_in_diary(ctx: Context, query: str, limit: int = 15) -> dict:
        return await run(ctx, lambda t: analysis.find(t, query, limit))

    # --- Buscar y añadir -------------------------------------------------------------------

    @server.tool(
        description=(
            "Busca alimentos para añadir: en lo que la persona ya ha comido (history), en sus "
            "recetas (recipes), en la base de datos propia (database) y, si no hay nada o "
            "open_food_facts=true, en Open Food Facts (productos de supermercado con marca). "
            "Cada resultado trae una `ref` para add_food / add_foods / plan_add / create_recipe "
            "y sus valores por 100 g."
        )
    )
    async def search_food(ctx: Context, query: str, limit: int = 8, open_food_facts: bool = False) -> dict:
        return await run(ctx, lambda t: diary.search(t, query, limit, open_food_facts))

    @server.tool(
        description=(
            "Añade un alimento o una receta de search_food (por su `ref`) a una comida de un día. "
            "unit: g, ml, serving (una ración) o package. Entra sin marcar como comido salvo eaten=true."
        )
    )
    async def add_food(
        ctx: Context, meal: str, ref: str, amount: float, unit: str = "g", date: str = "today", eaten: bool = False
    ) -> dict:
        return await run(ctx, lambda t: diary.add_food(t, date, meal, ref, amount, unit, eaten))

    @server.tool(
        description=(
            "Añade varios alimentos de una vez (hasta 40), en una sola escritura y un solo paso de "
            "deshacer. items: [{meal, ref, amount, unit, date, eaten}], cada uno como en add_food."
        )
    )
    async def add_foods(ctx: Context, items: list[dict]) -> dict:
        return await run(ctx, lambda t: diary.add_foods(t, items))

    @server.tool(
        description=(
            "Apunta algo por sus macros totales, sin producto (un plato de restaurante, algo que "
            "no está en ninguna base de datos). Los valores son del total, no por 100 g."
        )
    )
    async def add_quick_entry(
        ctx: Context,
        meal: str,
        name: str,
        kcal: float,
        proteins: Optional[float] = None,
        carbohydrates: Optional[float] = None,
        fats: Optional[float] = None,
        ingredients: Optional[list[str]] = None,
        date: str = "today",
        eaten: bool = False,
    ) -> dict:
        return await run(
            ctx, lambda t: diary.add_quick(t, date, meal, name, kcal, proteins, carbohydrates, fats, ingredients, eaten)
        )

    # --- Cambiar ---------------------------------------------------------------------------

    @server.tool(
        description=(
            "Cambia una entrada (id de get_day): cantidad y unidad, marcarla como comida o no, "
            "moverla a otra comida u otro día. Solo lo que se pase cambia."
        )
    )
    async def update_entry(
        ctx: Context,
        entry_id: str,
        amount: Optional[float] = None,
        unit: Optional[str] = None,
        eaten: Optional[bool] = None,
        meal: Optional[str] = None,
        date: Optional[str] = None,
    ) -> dict:
        return await run(ctx, lambda t: diary.update_entry(t, entry_id, amount, unit, eaten, meal, date))

    @server.tool(description="Marca (o desmarca con eaten=false) varias entradas como comidas de una vez.")
    async def set_eaten(ctx: Context, entry_ids: list[str], eaten: bool = True) -> dict:
        return await run(ctx, lambda t: diary.set_eaten(t, entry_ids, eaten))

    @server.tool(description="Borra una o varias entradas del diario (ids de get_day), en un solo paso de deshacer.")
    async def delete_entries(ctx: Context, entry_ids: list[str]) -> dict:
        return await run(ctx, lambda t: diary.delete_entries(t, entry_ids))

    @server.tool(description="Deshace el último cambio hecho desde este MCP.")
    async def undo_last(ctx: Context) -> dict:
        return await run(ctx, diary.undo)

    @server.tool(description="Vuelve a aplicar el último cambio que se deshizo.")
    async def redo(ctx: Context) -> dict:
        return await run(ctx, diary.redo)

    @server.tool(description="Los últimos cambios hechos desde este MCP, para revisarlos o deshacerlos.")
    async def recent_changes(ctx: Context) -> list[dict]:
        return await run(ctx, diary.history)

    # --- Borrador para planificar --------------------------------------------------------

    @server.tool(
        description=(
            "Empieza un borrador para planificar un día sin tocar el diario: luego plan_add, "
            "plan_show para ver cómo suma contra los objetivos, y plan_commit o plan_discard."
        )
    )
    async def plan_start(ctx: Context, date: str = "today") -> dict:
        return await run(ctx, lambda t: plans.start(t, date))

    @server.tool(description="Añade un alimento de search_food (por su ref) al borrador.")
    async def plan_add(ctx: Context, meal: str, ref: str, amount: float, unit: str = "g") -> dict:
        return await run(ctx, lambda t: plans.add(t, meal, ref, amount, unit))

    @server.tool(description="Quita un item del borrador (el número que dio plan_add o plan_show).")
    async def plan_remove(ctx: Context, item: int) -> dict:
        return await run(ctx, lambda t: plans.remove(t, item))

    @server.tool(
        description=(
            "El borrador: lo que ya hay ese día, lo añadido, el total, lo que falta para cada "
            "objetivo y cómo van los nutrientes que la persona quiere cumplir."
        )
    )
    async def plan_show(ctx: Context) -> dict:
        return await run(ctx, plans.show)

    @server.tool(
        description="Escribe el borrador en el diario de una vez (un solo deshacer). Sin marcar como comido salvo eaten=true."
    )
    async def plan_commit(ctx: Context, eaten: bool = False) -> dict:
        return await run(ctx, lambda t: plans.commit(t, eaten))

    @server.tool(description="Tira el borrador sin tocar el diario.")
    async def plan_discard(ctx: Context) -> dict:
        return await run(ctx, plans.discard)

    # --- Recetas ---------------------------------------------------------------------------

    @server.tool(
        description=(
            "Las recetas de la persona (las mismas que en la app), con raciones y kcal/macros por "
            "ración. query filtra por nombre."
        )
    )
    async def get_recipes(ctx: Context, query: Optional[str] = None) -> list[dict]:
        return await run(ctx, lambda t: diary.recipes.list(t, query))

    @server.tool(description="Una receta con sus ingredientes y cantidades (por id o nombre).")
    async def get_recipe(ctx: Context, recipe_id: str) -> dict:
        return await run(ctx, lambda t: diary.recipes.get(t, recipe_id))

    @server.tool(
        description=(
            "Crea una receta en la app: un plato hecho de alimentos reales con sus cantidades. "
            "ingredients: [{ref (de search_food) o recipe_id (de get_recipes), amount, unit}]. "
            "Luego se apunta buscándola con search_food (sale en recipes) y usando su ref."
        )
    )
    async def create_recipe(
        ctx: Context, name: str, servings: int, ingredients: list[dict], note: Optional[str] = None, liquid: bool = False
    ) -> dict:
        return await run(ctx, lambda t: diary.recipes.create(t, name, servings, ingredients, note, liquid))

    @server.tool(description="Borra una receta de la app (lo ya apuntado en el diario no cambia).")
    async def delete_recipe(ctx: Context, recipe_id: str) -> dict:
        return await run(ctx, lambda t: diary.recipes.delete(t, recipe_id))

    # --- Memoria -----------------------------------------------------------------------------

    @server.tool(
        description=(
            "Lo que la persona ha contado de sí misma (alergias, gustos, objetivos, rutinas), "
            "compartido con el asistente de la app. Míralo al empezar."
        )
    )
    async def get_memory(ctx: Context) -> dict:
        return await run(ctx, diary.memory.all)

    @server.tool(
        description=(
            "Recuerda algo de la persona para otras veces (key corta, p. ej. 'alergias'; value lo "
            "que sea). Si la key ya existe, se sustituye."
        )
    )
    async def remember(ctx: Context, key: str, value: str) -> dict:
        return await run(ctx, lambda t: diary.memory.remember(t, key, value))

    @server.tool(description="Olvida algo recordado (por su key).")
    async def forget(ctx: Context, key: str) -> dict:
        return await run(ctx, lambda t: diary.memory.forget(t, key))

    @server.custom_route("/health", methods=["GET"])
    async def health(request: Request) -> JSONResponse:
        return JSONResponse({"status": "ok"})

    return server, diary


class RequireToken:
    """Sin un token MCP válido, /mcp contesta 401 y dice dónde conseguir uno (OAuth). Es lo
    que hace que claude.ai abra la pantalla de iniciar sesión al añadir el conector.

    Quien decide es el servidor de sincronización: se le pregunta por el token (con un minuto
    de memoria) y solo valen tokens MCP, no los de un dispositivo emparejado."""

    def __init__(self, app, config: Config, transport: Optional[httpx.AsyncBaseTransport] = None):
        self.app = app
        self.sync_url = config.sync_url.rstrip("/")
        self.metadata = config.public_url.rstrip("/") + "/.well-known/oauth-protected-resource/mcp"
        self.transport = transport
        self.cache: dict[str, tuple[bool, float]] = {}

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or not scope["path"].startswith("/mcp"):
            return await self.app(scope, receive, send)
        auth = next((v.decode("latin-1") for k, v in scope["headers"] if k == b"authorization"), "")
        token = auth[7:].strip() if auth.lower().startswith("bearer ") else ""
        if not token:
            return await self._deny(scope, receive, send, None)
        valid = await self._valid(token)
        if valid is None:
            response = JSONResponse({"error": "temporarily_unavailable"}, status_code=503)
            return await response(scope, receive, send)
        if not valid:
            return await self._deny(scope, receive, send, "invalid_token")
        await self.app(scope, receive, send)

    async def _deny(self, scope, receive, send, error: Optional[str]):
        challenge = f'Bearer resource_metadata="{self.metadata}"'
        if error:
            challenge += f', error="{error}"'
        response = JSONResponse(
            {"error": error or "unauthorized", "error_description": "Hace falta un token MCP de Food You"},
            status_code=401,
            headers={"WWW-Authenticate": challenge},
        )
        await response(scope, receive, send)

    async def _valid(self, token: str) -> Optional[bool]:
        key = hashlib.sha256(token.encode()).hexdigest()
        hit = self.cache.get(key)
        if hit and hit[1] > time.monotonic():
            return hit[0]
        try:
            async with httpx.AsyncClient(base_url=self.sync_url, transport=self.transport, timeout=15) as client:
                r = await client.get(
                    "/v1/status",
                    headers={"Authorization": f"Bearer {token}", "X-Device-Id": "mcp", "User-Agent": "FoodYou-MCP"},
                )
        except httpx.HTTPError:
            return None
        if r.status_code >= 500:
            return None
        valid = r.status_code == 200 and r.json().get("access") == "mcp"
        if len(self.cache) > 1000:
            self.cache.clear()
        self.cache[key] = (valid, time.monotonic() + (60 if valid else 10))
        return valid


def asgi(
    server: MCPServer,
    config: Config,
    *,
    sync_transport: Optional[httpx.AsyncBaseTransport] = None,
    transport_security: Optional[TransportSecuritySettings] = None,
):
    inner = server.streamable_http_app(
        stateless_http=True,
        json_response=True,
        transport_security=transport_security
        or TransportSecuritySettings(
            enable_dns_rebinding_protection=True,
            allowed_hosts=[*config.allowed_hosts, *(f"{h}:*" for h in config.allowed_hosts)],
            allowed_origins=[],
        ),
    )
    wrapped = RequireToken(inner, config, sync_transport)
    # Uvicorn arranca el ciclo de vida (lifespan) de la app de dentro a través del envoltorio.
    return wrapped


def app(config: Optional[Config] = None):
    config = config or load()
    server, _ = build(config)
    return asgi(server, config)


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    uvicorn.run(app(), host="0.0.0.0", port=8000)
