# Herramientas del asistente de IA

Referencia de las **24 herramientas** que el modelo puede llamar, extraída directamente del código
(`app/src/commonMain/kotlin/com/maksimowiczm/foodyou/assistant/domain/tool/`). Al final está el
system prompt real y la explicación de cómo se le comunican las function calls.

La columna **Muta** indica si la herramienta cambia el diario. Las que mutan pasan por el *journal*
de cambios, así que dejan una entrada en `history` y admiten `undo`.

---

## Resumen

| Grupo | Herramientas |
|---|---|
| Lectura del diario | `dailyTotals`, `diaryRange`, `topFoods`, `topBrands`, `nutrientAttribution`, `searchDiary`, `mealTimingStats` |
| Contexto | `listMeals`, `goals` |
| Catálogo | `searchFood` |
| Escritura | `addEntries`, `updateEntry`, `deleteEntries`, `setEaten`, `createManualEntry`, `createRecipe` |
| Historial | `history`, `undo`, `redo` |
| Borrador (pad) | `padFromDay`, `padAdd`, `padRemove`, `padTotals`, `padCommit`, `padDiscard` |

---

## Lectura del diario

### `dailyTotals` — Muta: no

Totales nutricionales entre dos fechas, agrupables por día, día de la semana, semana, mes o
**comida** (desayuno/comida/cena...). Los días sin nada registrado también salen, con ceros, para
que el modelo no confunda "no hay dato" con "no comió".

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `from` | date | ✅ | Inicio del rango |
| `to` | date | ✅ | Fin del rango |
| `groupBy` | enum: `day`, `weekday`, `week`, `month`, `meal` | | Cómo agrupar. Por defecto `day` |
| `onlyEaten` | boolean | | Si es `true`, solo cuenta lo marcado como comido |
| `detailLevel` | enum: `basic`, `extended`, `full` | | Ver "Nivel de detalle" más abajo |

Con `groupBy=day` cada fila es `{"key":"2026-08-28", "kcal":..., "entries":5, "eaten":5}`. Con
`groupBy=meal` cambia a `{"mealId":1, "meal":"Breakfast", "kcal":..., ...}`.

### `diaryRange` — Muta: no

Las entradas del diario entre dos fechas, **con su `entryId`**. Es la única fuente de esos ids, así
que hay que llamarla antes de poder modificar o borrar nada. Sale **anidada por día y comida**, y
cada alimento lleva sus macros reales (lo que de verdad se comió) más un `per100g` de referencia
para poder recalcular otra cantidad sin volver a llamar a `searchFood`.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `from` | date | ✅ | Inicio del rango |
| `to` | date | ✅ | Fin del rango |
| `mealId` | integer | | Restringe a una comida |
| `onlyEaten` | boolean | | `true` solo lo comido, `false` solo lo planificado |
| `detailLevel` | enum: `basic`, `extended`, `full` | | Ver "Nivel de detalle" más abajo |

```json
{
  "2026-08-28": {
    "Breakfast": [
      {
        "entryId": 118,
        "name": "Pechuga de pollo (Hacendado)",
        "grams": 150.0,
        "kcal": 247.5, "proteins": 46.5, "carbohydrates": 0.0, "fats": 5.4,
        "per100g": { "kcal": 165.0, "proteins": 31.0, "carbohydrates": 0.0, "fats": 3.6 },
        "isEaten": true
      }
    ]
  }
}
```

> **Nota de diseño**: a diferencia de las demás consultas de lectura, `diaryRange` **no** fusiona las
> entradas manuales. Es deliberado: es la fuente de `entryId` para editar/borrar, y los
> autoincrementales de las tablas `Measurement` y `ManualDiaryEntry` pueden coincidir numéricamente.

### Nivel de detalle (`detailLevel`)

Compartido por `dailyTotals` y `diaryRange`, para no mandar 40 nutrientes cuando solo hacen falta 4.
Los niveles son **acumulativos**: `extended` no sustituye a los macros, los mantiene y añade más.

| Nivel | Añade |
|---|---|
| `basic` (por defecto) | `kcal`, `proteins`, `carbohydrates`, `fats` |
| `extended` | + `saturatedFats`, `sugars`, `addedSugars`, `dietaryFiber`, `salt`, `cholesterol`, `caffeine` |
| `full` | + un objeto `micronutrients` con las 13 vitaminas y 11 minerales |

El system prompt le dice al modelo que **no suba de nivel "por si acaso"** — solo si la pregunta es
específicamente sobre uno de esos nutrientes.

### `topFoods` — Muta: no

Los alimentos que más aparecen en el diario en un rango. Pensada para consultarla **antes** de
planificar: proponer lo que la persona ya come acierta más que elegir por cuenta propia.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `from` / `to` | date | ✅ | Rango |
| `limit` | integer | | Cuántos devolver. Por defecto 15 |
| `mealId` | integer | | Restringe a una comida concreta |

### `topBrands` — Muta: no

Las marcas que más aparecen, para proponer productos de las tiendas donde la persona compra.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `from` / `to` | date | ✅ | Rango |
| `limit` | integer | | Cuántas devolver. Por defecto 10 |

> **Limitación conocida**: en un diario con productos genéricos, el campo `brand` rara vez lleva una
> marca comercial real, así que agrupa descripciones libres ("2 unidades", "sin piel") como si lo
> fueran. La herramienta hace bien su trabajo; el dato de origen es el que es.

### `nutrientAttribution` — Muta: no

Qué alimentos aportaron un nutriente concreto en un rango, de mayor a menor. Es lo que permite
responder "de dónde me viene la grasa" o "qué recorto sin perder proteína".

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `nutrient` | enum (lista de nutrientes) | ✅ | Nutriente a atribuir |
| `from` / `to` | date | ✅ | Rango |
| `limit` | integer | | Cuántos devolver. Por defecto 10 |

### `searchDiary` — Muta: no

Busca un alimento dentro del **historial ya registrado**. No confundir con `searchFood`, que busca en
el catálogo: esto responde "¿cuándo comí salmón por última vez?".

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `query` | string | ✅ | Texto a buscar en el nombre |
| `from` / `to` | date | ✅ | Rango |

### `mealTimingStats` — Muta: no

A qué hora se suele **registrar** cada comida, promediado. La propia descripción avisa al modelo de
que es la hora de registro y no la de comer, y le pide que lo aclare al responder.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `from` / `to` | date | ✅ | Rango |

---

## Contexto

### `listMeals` — Muta: no

Las comidas configuradas (Desayuno, Comida...) con su `id` y su franja horaria. Sin esto no se puede
añadir nada, porque todas las escrituras piden `mealId`.

*Sin parámetros.*

### `goals` — Muta: no

Los objetivos nutricionales de una fecha. Devuelve dos bloques separados: `targets` con las cuatro
cifras que se preguntan de verdad (kcal, proteínas, carbohidratos, grasas) y `micronutrientTargets`
con el resto.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `date` | date | ✅ | Día cuyos objetivos se quieren |

> **Bug corregido**: energía y los tres macros **no** están en `DailyGoal.map` — viven aparte, en
> `macronutrientGoal`, y el mapa los excluye explícitamente. Al leer solo el mapa, el asistente veía
> decenas de micronutrientes y era **incapaz de decir cuántas kcal o cuánta proteína** tenía por
> objetivo la persona. El mismo fallo estaba en el system prompt.

---

## Catálogo

### `searchFood` — Muta: no

**La herramienta más importante del conjunto.** Busca alimentos y devuelve su `foodId` y sus macros
por 100 g. Es obligatoria antes de añadir nada: la regla sobre la que se sostiene todo el diseño es
que *el modelo no puede nombrar un alimento que no haya salido de aquí*. Si escribe "pechuga de
pollo, 150 g, 248 kcal" de memoria, la semana cuadra sobre el papel y es ficción.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `query` | string | | Qué buscar. Un término corto acierta más que una frase |
| `queries` | array de strings | | **Varias búsquedas a la vez** (máx. 10), en paralelo. Una de las dos es obligatoria |
| `limit` | integer | | Cuántos devolver por búsqueda. Por defecto 8 con `query`, 5 con `queries` |
| `sortBy` | enum (nutrientes) | | Ordena por densidad de ese nutriente por 100 g |

Además de productos devuelve **las recetas de la persona** que coincidan con la búsqueda (hasta 5,
y siempre las primeras), marcadas con `kind: "recipe"` y un `recipeId`, sus macros por 100 g, su
peso total y la lista de ingredientes con gramos. Cada producto lleva `kind: "product"` y su
`foodId`. Así el modelo reutiliza la hamburguesa que ya existe en vez de montar otra igual. Con
`sortBy` no se devuelven recetas: ordenar por nutriente es buscar ingredientes.

Un valor que el producto no tiene sale como `null` en `per100g`, no como `0`. Los productos a los
que les faltan calorías o macros van **al final** y llevan un campo `incomplete`: antes se
enseñaban con ceros y el modelo eligió una lechuga vacía que dejó el plato entero en rojo.

Con `queries` la respuesta es `{"results": [{"query": "...", "items": [...]}, ...]}`, en el mismo
orden en que se pidieron. Es la forma de buscar los ingredientes de un plato: **una** vuelta al modelo
en vez de una por ingrediente, que es donde se va el tiempo (cada vuelta son segundos; una búsqueda
local, milisegundos).

Las recetas se buscan en el índice de texto completo de la app, así que **ignoran tildes y
mayúsculas** ("albondigas" encuentra "Albóndigas").

> **Fallback de red**: si la consulta local no devuelve nada, dispara una búsqueda real contra las
> fuentes remotas activas (base custom, Open Food Facts, USDA) y la cachea antes de responder. Sin
> esto, el asistente solo veía el espejo local y devolvía vacío para cualquier alimento que la
> persona no hubiera buscado antes a mano. No se dispara si ya ha aparecido una receta: la persona
> tiene ese plato y no hace falta la búsqueda lenta.

---

## Escritura

### `addEntries` — Muta: **sí**

Añade alimentos o recetas al diario. Los `foodId` y `recipeId` tienen que venir de `searchFood` o
de `createRecipe`. Entran **sin marcar como comidos**: son una propuesta hasta que la persona los
marque.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `date` | date | ✅ | Día al que añadir |
| `mealId` | integer | ✅ | Comida (de `listMeals`) |
| `items[]` | array de objetos | ✅ | Ver abajo |

Cada objeto de `items[]` lleva **`foodId`** (un producto) **o `recipeId`** (una receta), y **una
de estas dos formas** de indicar la cantidad. Para una receta, `unit: "serving"` cuenta en raciones
(peso total ÷ raciones); sin unidad, en gramos.

| Forma | Parámetros | Cuándo |
|---|---|---|
| Cantidad fija | `amount` (número) + `unit` (opcional) | "Añade 150 g de pollo" |
| Objetivo de nutriente | `targetNutrient` (enum) + `targetAmount` (número) | "Necesito 20 g de proteína, ponme el pollo que corresponda" |

Con la segunda forma, la app calcula los gramos exactos (`100 × targetAmount / densidad_por_100g`)
en vez de que el modelo adivine o calcule a mano. `targetNutrient` acepta cualquiera de los 11
nutrientes de `NutrientSelector`: `energy`, `proteins`, `carbohydrates`, `fats`, `saturatedFats`,
`sugars`, `addedSugars`, `dietaryFiber`, `salt`, `cholesterol`, `caffeine`. Si el alimento tiene
densidad cero para ese nutriente (p. ej. pedir gramos de proteína de algo sin proteína), la
herramienta devuelve un error en vez de dividir por cero.

`padAdd` (más abajo) acepta exactamente el mismo par `targetNutrient`/`targetAmount`.

### `updateEntry` — Muta: **sí**

Cambia la cantidad, la comida o la fecha de una entrada existente.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `entryId` | integer | ✅ | Id de la entrada, de `diaryRange` |
| `amount` | number | | Cantidad nueva |
| `unit` | enum | | Unidad |
| `mealId` | integer | | Mover a otra comida |
| `date` | date | | Mover a otro día |

### `deleteEntries` — Muta: **sí**

Borra entradas. Los `entryId` salen de `diaryRange`.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `entryIds[]` | array de integer | ✅ | Entradas a borrar |

### `setEaten` — Muta: **sí**

Marca o desmarca entradas como comidas. Solo lo marcado cuenta en los totales del día.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `entryIds[]` | array de integer | ✅ | Entradas a cambiar |
| `eaten` | boolean | ✅ | `true` marca, `false` desmarca |

### `createManualEntry` — Muta: **sí**

La vía de escape para comida que no está en la base ("un pincho de tortilla del bar"). Guarda macros
estimadas por el modelo, sin referencia a ningún producto. Solo debe usarse cuando `searchFood` no
encuentre nada razonable.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `date` | date | ✅ | Día al que añadir |
| `mealId` | integer | ✅ | Comida |
| `name` | string | ✅ | Cómo llamarla; se ve tal cual en el diario |
| `kcal` | number | ✅ | Calorías **totales de la ración**, no por 100 g |
| `proteins` / `carbohydrates` / `fats` | number | | Gramos totales |
| `category` | enum (39 categorías) | | Tipo de alimento, para el icono del diario |

> Estas entradas se marcan internamente como creadas por el asistente, así que el diario las
> muestra con un icono de robot en vez del rayo del añadido rápido manual.

### `createRecipe` — Muta: **sí**

Un plato hecho de varias cosas que se comen juntas — hamburguesa, bocadillo, plato combinado,
ensalada — como **una receta de verdad**: cada ingrediente es un alimento del catálogo con su id y
sus gramos, y las macros **salen de los ingredientes**, nunca las escribe el modelo. Por eso luego
se puede cambiar el peso de un ingrediente, o el de la ración entera, y los números se recalculan.

Queda guardada en el catálogo, así que la próxima vez que la persona coma lo mismo es una búsqueda,
no reconstruirla. Con `date` y `mealId` además la añade al diario en el mismo paso.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `name` | string | ✅ | Nombre del plato |
| `ingredients[]` | array de objetos | ✅ | Lo que lleva el plato **entero** |
| `ingredients[].foodId` | integer | | Un producto de `searchFood` |
| `ingredients[].recipeId` | integer | | Otra receta como ingrediente (una salsa casera) |
| `ingredients[].amount` | number | ✅ | Cuánto lleva de eso |
| `ingredients[].unit` | enum | | Unidad; por defecto gramos (o ml si es líquido) |
| `servings` | integer | | Raciones que salen del plato entero. Por defecto 1 |
| `category` | enum | | Categoría para el icono (mismas que `createManualEntry`). Por defecto `PLATOS_PREPARADOS` |
| `isLiquid` | boolean | | Bebidas y sopas |
| `note` | string | | Nota de la receta |
| `date` + `mealId` | date + integer | | Para añadirla también al diario. Los dos o ninguno |
| `eatenAmount` + `eatenUnit` | number + enum | | Cuánto se ha comido si no es el plato entero |

**Todo o nada**: si un ingrediente no existe, no tiene cantidad, está en raciones y el alimento no
sabe cuánto pesa una ración, o **le faltan calorías o macros**, no se crea nada y el error dice cuál. Saltarlo en silencio daría un
plato con las macros mal.

La entrada queda marcada como creada por el asistente (robot en el diario, igual que con
`addEntries` y `padCommit`).

Sin `eatenAmount` se añade el plato entero, en gramos (o ml): en el diario se lee mejor que "1
envase" y se edita igual.

**Deshacer** se lleva la entrada del diario y la receta a la vez (un solo punto de historial).
Rehacer la devuelve **con el mismo `recipeId`**, para que el modelo no se quede apuntando a nada.

> El flujo que pide el prompt: buscar primero el plato con `searchFood`; si ya es una receta suya,
> `addEntries` con su `recipeId`. Si no, buscar cada ingrediente por separado y pasarlos aquí.

> **Sustituye a `createComposedEntry`**, que guardaba el plato como una entrada manual con las
> macros fijas y los ingredientes como simple texto. Las entradas que ya se crearon así siguen en
> el diario tal cual; simplemente ya no se crean nuevas.

---

## Historial

### `history` — Muta: no

Los últimos cambios que ha hecho el asistente, con su `changeId` y si siguen aplicados.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `limit` | integer | | Cuántos devolver. Por defecto 10 |

### `undo` — Muta: **sí**

Revierte un cambio. Sin `changeId` revierte el último que siga aplicado. Un plan completo del pad
deja **un solo** punto de historial, así que se deshace entero de una vez.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `changeId` | integer | | Id del cambio, de `history` |

### `redo` — Muta: **sí**

Vuelve a aplicar el último cambio revertido. *Sin parámetros.*

---

## Borrador (pad)

El pad es un área de trabajo en memoria: el modelo prueba combinaciones, suma y descarta sin tocar el
diario, y solo escribe al final. Vive mientras dure la conversación y **no sobrevive a que el sistema
mate el proceso de la app**.

### `padFromDay` — Muta: no

Empieza un borrador copiando lo que ya hay en un día.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `date` | date | ✅ | Día del que partir |

### `padAdd` — Muta: no

Añade un alimento o una receta al borrador. Devuelve un `ref` que sirve para quitarlo luego. Acepta la misma
cantidad fija o el mismo objetivo de nutriente que `addEntries` (ver su sección arriba).

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `date` | date | ✅ | Día del borrador |
| `mealId` | integer | ✅ | Comida a la que iría |
| `foodId` | integer | | Un producto: el `foodId` de `searchFood` |
| `recipeId` | integer | | Una receta: el `recipeId` de `searchFood`. Uno de los dos es obligatorio |
| `amount` | number | | Cantidad. Omitir si se usa `targetNutrient` |
| `unit` | string | | Unidad. Por defecto gramos, o mililitros si es líquido |
| `targetNutrient` / `targetAmount` | enum / number | | Alternativa a `amount`: calcula los gramos para llegar a esta cantidad de un nutriente |

### `padRemove` — Muta: no

Quita un alimento del borrador por su `ref`.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `ref` | integer | ✅ | El `ref` que devolvió `padAdd` |

### `padTotals` — Muta: no

Los totales del borrador y lo que queda para el objetivo. Existe para que el modelo **no sume de
cabeza**: las sumas las hace la app y no se equivoca.

| Parámetro | Tipo | Req. | Descripción |
|---|---|---|---|
| `targetKcal` | number | | Objetivo de calorías, para dar la diferencia |
| `targetProteins` | number | | Objetivo de proteína en gramos |

### `padCommit` — Muta: **sí**

Escribe el borrador en el diario, sin marcar como comido. Lo que ya estaba en el día no se toca.
Deja un único punto de historial. *Sin parámetros.*

### `padDiscard` — Muta: no

Descarta el borrador entero. No deja rastro. *Sin parámetros.*

---

# Herramientas en paralelo

Si el modelo pide varias herramientas en la misma respuesta y **todas** son de lectura pura
(`searchFood`, `dailyTotals`, `diaryRange`, `topFoods`, `topBrands`, `nutrientAttribution`,
`searchDiary`, `mealTimingStats`, `listMeals`, `goals`, `history`), se ejecutan a la vez. Basta con
que una escriba para que todas vayan en orden: lo que viene después puede depender de ella, y el
journal registra los cambios en el orden en que pasan. Las del borrador tampoco van en paralelo
aunque no toquen el diario: modifican el borrador en memoria y se pisarían. Lo decide cada
herramienta con `runsConcurrently` (por defecto `false`), no `mutates`.

# Cómo se le comunican las function calls

**No hay ningún texto en el prompt que liste las herramientas.** Se envían por el campo `tools` de la
API de chat completions (formato OpenAI), que es lo que DeepSeek espera. En
`AgentLoop.run()` se construyen así:

```kotlin
val toolSpecs =
    registry.tools.map { tool ->
        ToolSpec(
            function =
                FunctionSpec(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters,
                )
        )
    }
```

Cada `parameters` es un JSON Schema construido con el helper `ToolSchema`. Por ejemplo, `goals`
viaja como:

```json
{
  "type": "function",
  "function": {
    "name": "goals",
    "description": "Los objetivos nutricionales para una fecha concreta.",
    "parameters": {
      "type": "object",
      "properties": {
        "date": { "type": "string", "description": "Dia cuyos objetivos quieres." }
      },
      "required": ["date"]
    }
  }
}
```

Dos detalles no obvios de la implementación:

1. **El orden es estable (alfabético, fijado por el registro) y el bloque va al principio de cada
   petición.** DeepSeek solo sirve estas definiciones desde su caché de prompt si el prefijo es
   byte a byte idéntico; si el orden bailara, una conversación de seis llamadas pagaría las
   definiciones seis veces.
2. **Los errores de herramienta se devuelven al modelo como texto JSON**, no como excepción:
   `{"ok": false, "error": "..."}`. Así puede leerlos y corregirse, en vez de reventar la
   conversación.

---

# System prompt

Se construye en cada turno (`SystemPromptBuilder`), porque inyecta contexto que el modelo no puede
saber: la fecha, las comidas configuradas, los objetivos y lo que la persona le ha contado.

> Existe por un fallo encontrado probando, no razonando: preguntado "cuántas calorías llevo hoy",
> DeepSeek llamaba a la herramienta con `from: "2025-05-09"` — una fecha inventada, más de un año
> desviada. El modelo no tiene reloj ni calendario.

**Está escrito en inglés a propósito**, aunque el asistente hable siempre en el idioma de quien
pregunta: este texto se manda entero en cada petición y nadie lo lee directamente, así que su
idioma solo afecta al coste en tokens — el propio prompt le dice al modelo que responda en el
idioma de la persona.

Las partes entre `${...}` se sustituyen en tiempo de ejecución.

```text
You are the Food You assistant, a food diary app. Speak the person's language, whatever it is.

## Date and time
Today is ${today} (${dayName}). Time zone: ${timeZone}.
When someone says "today", "yesterday", "this week" or "last month", compute dates from that. NEVER
guess a date: if unsure, use this one as-is.

## Configured meals
${mealLines}          → e.g. "- id 1: Breakfast (06:00 - 10:00)"
You need the meal id to add anything to the diary.

## Today's goals
${goalLines}          → "- Energy: 2000 kcal", "- Protein: 100 g", etc.

## What this person has told you
${memoryLines}        → persistent memory, or "(nothing yet)"

## How you work

1. Food can ONLY come from searchFood. Never invent a food or its macros: writing "chicken breast,
   150 g, 248 kcal" from memory makes the plan add up on paper while being fiction. With searchFood
   the database supplies the macros; you only choose the amount. If something is not in the
   database, use createManualEntry and say it is an estimate. Always give it a category: without
   one the diary draws a grey question mark instead of an icon.

   If someone describes or shows you a photo of a composite dish ("pork loin sandwich", "salad with
   tuna and cheese"), do NOT search for the whole dish at once: it will not be in the database, and
   you would not know its weight anyway. Break it into its recognisable ingredients and call
   searchFood separately for each one (e.g. "bread", "cured pork loin"), estimating the grams of
   each yourself from what you see or are told. Use createManualEntry only for the specific
   ingredient that, after searching, really is not in the database - never for the whole dish
   without having tried it ingredient by ingredient.

   Both addEntries and padAdd accept an amount either as a fixed quantity (amount+unit) or as a
   nutrient target (targetNutrient+targetAmount, e.g. "I need 20 g of protein, add the chicken for
   that"). Prefer the target form whenever the person names a nutrient amount rather than a weight
   - let the tool compute the grams, never do that math yourself.

2. To plan, work in the draft first: padFromDay, padAdd, padTotals, and padCommit once it adds up.
   NEVER sum macros in your head - that is what padTotals is for, and it never gets it wrong. The
   draft is yours; do not show it or explain it - the person only cares about the result.

3. Everything you add - addEntries, createManualEntry or the draft - goes in UNCHECKED: it is a
   proposal until the person marks it eaten. Do not claim something is eaten just because you added
   it.

4. Before proposing food, check topFoods and topBrands: proposing what the person actually eats
   beats picking on your own, and that is where their brands come from without anyone telling you.

5. You can undo what you do. If you make a mistake, use undo rather than fixing it by hand.

6. dailyTotals and diaryRange return kcal/protein/carbohydrates/fat by default (detailLevel=basic).
   Only raise it to "extended" if asked about saturated fat, sugar, fibre, salt, cholesterol or
   caffeine; only raise it to "full" if asked about a specific vitamin or mineral. Do not raise it
   "just in case" - each level returns a lot more text.

## When answering

Be brief and to the point. Give the numbers that matter and do not repeat what the person already
sees on screen. Do not list the tools you used or describe your process - report the result.
```

---

## El bucle agéntico

`AgentLoop` manda el mensaje, ejecuta las herramientas que vengan de vuelta, manda los resultados, y
repite **hasta que el modelo responda con texto en vez de con `tool_calls`**. Esa es la salida
natural del bucle.

El tope de pasos es configurable en Ajustes (8 / 16 / 32 / 64 / 128 / **Sin límite**). Internamente
`maxIterations <= 0` significa sin tope; incluso entonces el bucle sigue teniendo la salida natural
de arriba.

Dos comportamientos del bucle que conviene conocer:

- **Un único modelo para toda la conversación.** Ajustes ya no tiene un "modelo de texto" y un
  "modelo de visión" separados — hay un solo `model`, y un interruptor `supportsVision` que decide
  si el icono de la cámara aparece en el chat. Antes había dos modelos y el bucle cambiaba entre
  ellos turno a turno según si ese mensaje concreto traía una foto; como el historial completo
  viaja en cada petición, un turno de solo texto justo después de uno con foto acababa yendo a un
  modelo sin visión con una imagen todavía en el historial, y la API lo rechazaba con
  `HTTP 400: "This model does not support image"`. Con un único modelo ese fallo es imposible: si
  no admite imágenes, la cámara no aparece nunca; si las admite, se usa siempre el mismo modelo.
  Las imágenes solo se despojan del historial como red de seguridad, por si queda una foto de antes
  de que alguien apague el interruptor de visión.
- **Si se agotan los pasos, el turno termina en error pero las mutaciones ya aplicadas persisten.**
  El mensaje de error dice que el diario no se ha tocado, lo cual puede ser falso si el modelo
  alcanzó a ejecutar una herramienta de escritura antes de quedarse sin pasos.
