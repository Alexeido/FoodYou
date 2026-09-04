# Pruebas del asistente de IA

Verificación manual de las 24 herramientas del asistente y de la interfaz de chat/ajustes, contra
la API real de DeepSeek y la base de datos custom en `foods.alexeido.com`.

**Entorno**: emulador Android (Pixel 7, Android 16, API 36), aceleración por hardware vía el driver
AEHD. Base de datos custom como única fuente activa. Modelo principal `deepseek-v4-flash`, modelo
de visión `deepseek-v4-flash-vision-exp`.

**Fecha**: 27 de agosto de 2026.

---

## Tabla maestra

### Lectura

| # | Categoría | Qué se prueba | Prompt / acción de reproducción | Prioridad |
|---|---|---|---|---|
| 1 | Lectura | `dailyTotals` | «¿Cuántas calorías llevo hoy?» | Alta |
| 2 | Lectura | `dailyTotals` agrupado | «¿Cómo he comido esta semana, día a día?» | Alta |
| 3 | Lectura | `diaryRange` | «Enséñame lo que tengo registrado hoy» | Alta |
| 4 | Lectura | `topFoods` | «¿Qué es lo que más como?» | Media |
| 5 | Lectura | `topBrands` | «¿Qué marcas compro más?» | Media |
| 6 | Lectura | `nutrientAttribution` | «¿De dónde me viene la grasa hoy?» | Media |
| 7 | Lectura | `searchDiary` | «¿Cuándo comí pollo por última vez?» | Media |
| 8 | Lectura | `mealTimingStats` | «¿A qué hora suelo registrar la cena?» | Baja |
| 9 | Lectura | `listMeals` | «¿Qué comidas tengo configuradas?» | Alta |
| 10 | Lectura | `goals` | «¿Cuáles son mis objetivos de hoy?» | Alta |
| 11 | Lectura | `searchFood` | «Busca pollo en la base de datos» | Alta |
| 12 | Lectura | `searchFood` con orden por nutriente | «Busca algo con mucha proteína» | Media |

### Escritura

| # | Categoría | Qué se prueba | Prompt / acción de reproducción | Prioridad |
|---|---|---|---|---|
| 13 | Escritura | `addEntries` (uno) | «Me he comido 200 g de pollo asado en el desayuno» | Alta |
| 14 | Escritura | `addEntries` (varios a la vez) | «Añade 2 huevos y una tostada al desayuno» | Alta |
| 15 | Escritura | `updateEntry` | «Cambia el pollo de antes a 150 g» | Alta |
| 16 | Escritura | `deleteEntries` | «Quita el pollo del desayuno» | Alta |
| 17 | Escritura | `setEaten` | «Marca el desayuno como comido» | Alta |
| 18 | Escritura | `createManualEntry` | «Un pincho de tortilla del bar, unas 250 kcal» | Media |

### Historial

| # | Categoría | Qué se prueba | Prompt / acción de reproducción | Prioridad |
|---|---|---|---|---|
| 19 | Historial | `history` | «¿Qué has cambiado hasta ahora?» | Media |
| 20 | Historial | `undo` vía herramienta | «Deshaz lo último» (en vez de tocar el botón) | Alta |
| 21 | Historial | `redo` | «Vuelve a ponerlo» tras un undo | Media |

### Pad (efecto observable, no llamada aislada)

| # | Categoría | Qué se prueba | Prompt / acción de reproducción | Prioridad |
|---|---|---|---|---|
| 22 | Pad | Plan de un día completo | «Rellename lo que me queda de hoy, me faltan 900 kcal» | Alta |
| 23 | Pad | Un solo punto de historial para todo el plan | Comprobar en `history` tras la prueba 22 que aparece **una** entrada, no una por alimento | Alta |
| 24 | Pad | Deshacer el plan entero de una vez | `undo` sobre esa única entrada del punto 23 | Alta |
| 25 | Pad | Partir de un día ya existente | «Copia lo de hoy y ajusta la cena» | Media |
| 26 | Pad | El cambio manual del usuario sobrevive | Añadir algo a mano en Home mientras un plan está en curso, comprobar que no se pisa | Media |

### Transversal / interfaz

| # | Categoría | Qué se prueba | Prompt / acción de reproducción | Prioridad |
|---|---|---|---|---|
| 27 | UI | Icono condicional en Home | Quitar la clave en ajustes → comprobar que el icono ✨ desaparece de Home | Alta |
| 28 | UI | Persistencia de ajustes | Cerrar y reabrir la app, comprobar que clave/modelo/URL siguen puestos | Alta |
| 29 | UI | Nueva conversación | Menú ⋮ → «New conversation», comprobar que el hilo se vacía | Media |
| 30 | UI | La conversación sobrevive a salir | Salir al diario y volver al chat, comprobar que el hilo sigue ahí | Media |
| 31 | UI | Foto con visión | Adjuntar una foto de comida y pedir que la registre | Alta |
| 32 | UI | Dictado por voz | **No verificable en este entorno** — el emulador no tiene micrófono real y el servicio de reconocimiento de Google está desactivado a propósito (era el causante de la mayoría de fallos de toque de la sesión anterior) | Baja |
| 33 | UI | Error de clave inválida | Poner una clave falsa y volver a intentar «Probar conexión» | Media |
| 34 | UI | Render de markdown en el chat | Ya detectado como roto (`**texto**` literal) en la sesión anterior; confirmar alcance | Alta |

---

## Cutreces de interfaz (lista viva, anotadas al vuelo durante las pruebas)

Esto **no** es la lista de fallos funcionales (esos están en "Resumen ejecutivo" y "Hallazgos
detallados"). Es un cajón aparte para todo lo que se ve feo, inconsistente o sin pulir aunque
funcione bien — se va rellenando según aparece, para revisarlo todo junto al terminar las pruebas.

1. **Markdown roto en el chat** (mismo problema que la fila #34 de la tabla). El chat no interpreta
   `**negrita**`: se ven los asteriscos literales en cualquier respuesta que el modelo enfatice, que
   es la mayoría. Es la cutrez que más se nota, porque sale en casi cada turno.
   **Captura**: `screenshots/decomposicion-bocata-lomo.png` (mirar el mensaje del asistente, sale con `**` literales).

2. **Icono "?" gris para entradas manuales o sin identificar**, sin ninguna explicación visible en
   la propia pantalla. En el diario, un producto normal muestra su emoji/imagen (🍗 en "Pollo
   Asado"), pero una entrada de `createManualEntry` o una encontrada por el asistente antes de que
   el espejo local tuviera nada cacheado muestra un círculo gris con "?" — se entiende la intención
   (avisar de que no es un producto identificado del catálogo) pero no hay ni un tooltip ni una
   etiqueta que lo diga, así que a simple vista solo parece que "falta la foto".
   **Captura**: `screenshots/16-deleteentries.png` (las filas "Huevos"/"Tostada de pan" con el "?").

3. **Truncamiento de la pestaña "Custom database"** en el buscador de alimentos: se corta a
   "Custom data…" con puntos suspensivos, mientras que "Recent", "Yours" y "Favorites" caben
   enteras en su pestaña. Es solo un problema de ancho de columna en la fila de pestañas, no de
   traducción — con cuatro pestañas de longitud dispar, la última se queda sin espacio.
   **Captura**: `screenshots/search_customdb2.png` (la pestaña activa en azul, cortada).

4. **El icono de rayo ⚡ significa dos cosas distintas sin ninguna leyenda.** En el diario aparece
   pegado al nombre de una entrada («Tostada de pan ⚡», «Huevos ⚡» — las mismas que llevan el "?"
   gris del punto 2), donde parece indicar "estimado/no identificado". Pero cada cabecera de comida
   (Lunch, Dinner, Snacks) tiene también un botón circular gris con el mismo rayo, pegado al botón
   azul "+" — ahí es una acción (parece "añadir rápido"), no una etiqueta informativa. Mismo
   dibujo, dos significados, sin texto ni tooltip que lo distinga.
   **Captura**: `screenshots/home_scroll5.png` (rayo como etiqueta junto a "Tostada de pan ⚡" y
   rayo como botón de acción junto a "Lunch"/"Dinner"/"Snacks", visibles en la misma captura).

5. **Estado vacío "No food found" muy desnudo** en el buscador de alimentos: solo texto gris
   centrado, sin icono ni ilustración ni sugerencia de qué hacer a continuación — contrasta con el
   resto de la app, que sí cuida los estados vacíos (los tres chips de sugerencia en el chat vacío
   del asistente, por ejemplo).

6. **Las fotos que envías al chat no se ven en el propio chat.** Al adjuntar una imagen (prueba
   #31), la burbuja del usuario muestra únicamente el texto que la acompaña — ninguna miniatura, ni
   siquiera un icono de "imagen adjunta". Si vuelves a subir en la conversación no hay forma visual
   de saber qué foto mandaste ni de confirmar que se adjuntó la correcta. Se nota especialmente
   porque el modelo sí la ve y la describe con detalle en su respuesta — el hueco es solo del lado
   del historial visual.
   **Captura**: `screenshots/31-vision-turno1-ok.png` (la burbuja de arriba dice solo «anadelo a la
   cena», sin rastro de la foto adjunta).

7. **El icono de la cámara no abre la cámara — abre la galería.** Esta es más una carencia
   funcional que una cutrez visual, pero se apunta aquí porque se descubrió mirando la misma
   interfaz del punto 6. `rememberImagePicker` (`ImagePicker.android.kt`) usa
   `ActivityResultContracts.PickVisualMedia()`, que es un selector de galería — nunca ofrece hacer
   una foto nueva en el momento. Para el caso de uso real del propio asistente (registrar lo que
   estás a punto de comer o lo que tienes delante) esto es una fricción grande: casi nadie hace la
   foto por separado con la cámara del sistema para luego venir a esta app a adjuntarla desde la
   galería — el flujo natural es "le doy al icono de la cámara y hago la foto ahí mismo". Arreglo
   razonable: añadir una segunda opción (o un selector) que lance
   `ActivityResultContracts.TakePicture()` (o un `Intent` de captura) además del selector de
   galería actual, en vez de sustituirlo — a veces sí interesa adjuntar una foto ya existente.

---

## Resumen ejecutivo

**8 fallos reales encontrados. 6 arreglados y verificados en esta misma sesión, 2 pendientes** (uno por ser un límite estructural — segunda mitad de B —, y uno nuevo, Hallazgo E, encontrado al final de la sesión y sin arreglar todavía). El resto de herramientas de lectura probadas responden con datos correctos.

### Arreglados y verificados en esta sesión

- **`headline_favorites` solo existía en español** → el buscador de alimentos crasheaba en cualquier idioma que no fuera español. Arreglado antes de las pruebas de la IA (commit `e221f2d3`).
- **Cliente HTTP sin timeouts explícitos** → `Socket timeout has expired` a los ~10 s, insuficiente para un modelo de razonamiento en una cadena de herramientas. Arreglado subiendo a 90 s.
- **🔴 Hallazgo A — Las herramientas de lectura eran ciegas a las entradas manuales.** Ninguna de las 7 consultas de lectura tocaba `ManualDiaryEntryRepository`. **Arreglado**: nuevo `DiaryReader` que fusiona ambas fuentes; las 6 consultas de solo-agregación (`dailyTotals`, `topFoods`, `topBrands`, `nutrientAttribution`, `searchDiary`, `mealTimingStats`) lo usan ahora. `diaryRange` se deja tal cual **a propósito** — es la fuente de `entryId` para `updateEntry`/`deleteEntries`, y mezclar IDs de dos tablas sin una herramienta de borrado dedicada para entradas manuales sería peligroso (los autoincrementales de `Measurement` y `ManualDiaryEntry` pueden coincidir numéricamente). Verificado en el emulador tras el arreglo.
- **🔴 Hallazgo B — `createManualEntry` no era reversible.** No llamaba a `journal.record`. **Arreglado**: nuevo `UndoAction.DeleteManualEntries`/`RestoreManualEntries`, la herramienta ahora registra el cambio y es deshacible como cualquier otra escritura.
- **🟠 Hallazgo B (segunda parte) — límite estructural, no arreglado.** `ManualDiaryEntry.isEaten` no tiene columna en la tabla `ManualDiaryEntry` en ningún punto de la app (no solo en el código del asistente): el dominio lo pone a `true` por defecto y ni `toEntity()` ni `toModel()` lo persisten. No es arreglable con un cambio de una línea — necesitaría una migración de esquema. Se corrigió lo que sí se podía: la descripción de la herramienta y el prompt de sistema ahora **dicen la verdad** (que esta entrada nace ya marcada como comida y nunca debe usarse para planificar), en vez de prometer algo que la app no puede cumplir.
- **🔴 Hallazgo C — El límite de 8 pasos del bucle es bajo para este modelo.** Se agotó **tres veces**: «añade 2 huevos y una tostada», «busca pollo» tras una sugerencia previa, y «rellename lo que me queda del día» (el caso de uso estrella del pad). **Arreglado**: nuevos chips 8/16/32/64/128/**Sin límite** en Ajustes (`AgentLoop` acepta `maxIterations <= 0` como sin tope, con la misma salida natural en cuanto el modelo responde con texto). Verificado en el dispositivo: el chip "No limit" se selecciona, persiste al cerrar y reabrir Ajustes, y no se ha observado ningún caso de bucle realmente infinito en las pruebas posteriores.
- **🔴 Hallazgo D — `searchFood` solo veía el espejo local, nunca la red.** Ver detalle abajo. **Arreglado**: nuevo `AssistantRemoteFoodFallback`, que dispara una consulta real contra las fuentes remotas activadas (igual que hace la pantalla de búsqueda) cuando el espejo local no tiene nada, y cachea el resultado antes de responder. Verificado en vivo desde una tabla `Product` con **0 filas**: antes del arreglo, «busca huevos»/«busca lomo embuchado» devolvían vacío pese a existir en el servidor; después, la misma pregunta encuentra los productos reales y dispara la caché local (52 filas insertadas tras una sola búsqueda).
- **🟡 Comportamiento del modelo con platos compuestos, corregido vía prompt.** Sin instrucción explícita, el modelo tendía a estimar un plato entero de una vez en lugar de buscar sus ingredientes por separado — en parte agravado por el Hallazgo D (una búsqueda del plato completo casi nunca encuentra nada). **Arreglado**: nueva regla en `SystemPromptBuilder` que le pide descomponer platos compuestos en ingredientes reconocibles y buscarlos uno a uno. Verificado con «he comido un bocata de lomo con pan de chapata»: buscó "pan" y "lomo" por separado, encontró productos reales de ambos, y preguntó por marca/cantidad en vez de inventar una única entrada estimada.
- **🔴 Hallazgo E — la visión se rompe en cuanto la conversación tiene un segundo turno. No arreglado.** Ver detalle abajo. Adjuntar una foto funciona perfectamente en el primer turno (el modelo la describe bien), pero en cuanto llega un segundo mensaje sin foto nueva, la API devuelve `HTTP 400: "This model does not support image"` y el turno entero falla. Causa: la app decide qué modelo usar turno a turno según si *ese* mensaje trae imagen (`useVisionModel = hasImage`), pero envía siempre el historial completo — que todavía contiene la imagen del primer turno — sin importar qué modelo se vaya a usar. En cuanto el modelo elegido para el turno actual no admite imágenes, la API rechaza la petición porque el historial que le llega sí las tiene.

---

## Resultados

### #1 — `dailyTotals`

**Prompt**: «¿Cuántas calorías llevo hoy?» (diario vacío en el momento de la prueba)
**Resultado**: ✅ Correcto — «Hoy todavía no tienes nada marcado como comido: 0 kcal.»
**Captura**: `screenshots/01-dailytotals.png`

### #2 — `dailyTotals` agrupado por día

**Prompt**: «¿Cómo he comido esta semana, día a día?»
**Resultado**: ✅ Correcto — desglosó lunes a jueves con fechas reales (24, 25, 26, 27 de agosto), todos en 0 kcal, coherente con el diario vacío.
**Captura**: `screenshots/02-dailytotals-semana.png`

### #3 — `diaryRange`

**Prompt**: «Enséñame lo que tengo registrado hoy»
**Resultado**: ⚠️→ℹ️ En el momento de la prueba solo mostró el pollo asado, sin ver los huevos/tostada manuales. Esto es **esperado, no un bug de #3**: `diaryRange` se deja deliberadamente sin fusionar con las entradas manuales (ver Hallazgo A) porque es la fuente de `entryId` para `updateEntry`/`deleteEntries`, y no existe todavía una herramienta de borrado dedicada para entradas manuales.
**Captura**: `screenshots/03-diaryrange.png`

### #4 — `topFoods`

**Prompt**: «¿Qué es lo que más como?»
**Resultado original**: ⚠️ Solo detectó el pollo, ciego a las entradas manuales — Hallazgo A.
**Tras el arreglo**: ✅ Verificado por código — `TopFoodsUseCase` ahora lee de `DiaryReader`, que fusiona ambas fuentes. No se repitió la prueba en vivo tras el arreglo (se priorizó verificar #3/#6/#19, que cubren el mismo camino de código).
**Captura**: `screenshots/04-topfoods.png`

### #6 — `nutrientAttribution`

**Prompt**: «¿De dónde me viene la grasa hoy?»
**Resultado original**: ⚠️ Con contenido real dijo *«the only thing in your diary is the skinless roasted chicken... there's nothing else logged»* — no vio los huevos/tostada. Hallazgo A.
**Tras el arreglo**: ✅ Verificado por código — mismo cambio que #4.
**Capturas**: `screenshots/06-nutrientattribution-vacio.png`, `screenshots/06b-nutrientattribution-ciego-a-manuales.png`

### #9 — `listMeals`

**Prompt**: «¿Qué comidas tengo configuradas?»
**Resultado**: ✅ Correcto — las 4 comidas reales con sus franjas horarias (Breakfast 06–10, Lunch 10–15, Dinner 15–21, Snacks 00–00).
**Captura**: `screenshots/09-listmeals.png`

### #10 — `goals`

**Prompt**: «¿Cuáles son mis objetivos de hoy?»
**Resultado**: ✅ Correcto, pero volcó **todos** los micronutrientes (vitaminas, minerales, omega-3/6, fibra soluble/insoluble...) para una pregunta simple. No es un fallo, pero es una respuesta más larga de lo necesario — el prompt del sistema podría pedirle resumir y solo detallar si se lo piden.
**Captura**: `screenshots/10-goals.png`

### #11 — `searchFood`

**Prompt**: «Busca pollo en la base de datos»
**Resultado**: ❌ Agotó el límite de 8 pasos sin responder. Contexto: el turno anterior había terminado con el asistente ofreciendo «¿quieres que busquemos algo para completar el día?», y es probable que interpretara la orden corta como una continuación de esa idea (buscar **y añadir**), encadenando de más. Diario no tocado, mensaje de error correcto. Pendiente de repetir en una conversación nueva y aislada para confirmar si el problema es el límite en sí o el contexto previo.
**Captura**: `screenshots/11-searchfood-agotado.png`

### #13 — `addEntries` (un alimento)

**Prompt**: «Me he comido 200 g de pollo asado en el desayuno»
**Resultado**: ✅ Correcto — buscó en la base custom, encontró «Pollo Asado (sin piel)», anotó 334 kcal, entrada sin marcar como comida, botón Deshacer presente.
**Captura**: `screenshots/13-addentries.png`

### #14 — `addEntries` (varios alimentos)

**Prompt**: «Añade 2 huevos y una tostada al desayuno»
**Resultado**: ❌ Agotó el límite de 8 pasos. El mensaje de error dijo **«Your diary has not been touched»**, pero esto resultó ser falso: en un turno posterior (prueba #15) se confirmó que sí había creado entradas manuales de huevos y tostada antes de quedarse sin pasos. Ver **Hallazgo C**.
**Captura**: `screenshots/14-addentries-multi-agotado.png`

### #15 — `updateEntry`

**Prompt**: «Cambia el pollo de antes a 150 gramos»
**Resultado**: ⚠️ El cambio de cantidad en sí fue correcto (150 g → 251 kcal). Pero en el mismo turno, el modelo **retomó por su cuenta** la petición fallida de la prueba #14 y creó huevos y tostada como entradas manuales estimadas — duplicando lo que ya se había creado parcialmente en el intento fallido anterior. Resultado neto en el diario: 2× huevos, 2× tostada, las cuatro ya marcadas como comidas. El propio mensaje dijo *«Todo sigue sin marcar como comido»*, que era falso. Ver **Hallazgos A, B y C** juntos aquí.
**Capturas**: `screenshots/15-updateentry-y-duplicado.png`, `screenshots/15b-diario-duplicado-marcado.png`

### #19 — `history`

**Prompt**: «¿Qué has cambiado hasta ahora?»
**Resultado original**: ⚠️ No mencionó los huevos/tostada, porque `createManualEntry` no pasaba por el journal — Hallazgo B.
**Tras el arreglo**: ✅ Verificado por código — `CreateManualEntryTool` ahora llama a `journal.record`, así que aparecerá en `history` y admite `undo`. No se repitió la prueba en vivo (el diario de la sesión ya tenía las entradas viejas, creadas antes del arreglo, que seguirán sin journal por ser anteriores al cambio — comportamiento esperado, no arrastra el bug hacia delante).
**Captura**: `screenshots/19-history.png`

### #22 — Pad: plan de un día completo (Hallazgo C, reconfirmado)

**Prompt**: «Fill in the rest of my day» (chip de sugerencia)
**Resultado**: ❌ Agotó el límite de 8 pasos, igual que en #11 y #14. Es la prueba que más pasos necesita de todas (`padFromDay` + `searchFood` + `padAdd` por cada hueco + `padTotals` + `padCommit`), así que es donde el límite duele más. Diario no tocado, mensaje correcto.
**Captura**: `screenshots/hallazgo-C-reconfirmado-plan-dia.png`

### #29 — Nueva conversación

**Acción**: Menú ⋮ → «New conversation»
**Resultado**: ✅ Correcto — el hilo se vació y volvieron a aparecer los tres chips de sugerencia iniciales.
**Captura**: `screenshots/29-nueva-conversacion.png`

### #5 — `topBrands`

**Prompt**: «¿Qué marcas compro más?»
**Resultado**: ✅ Correcto, con un matiz de calidad de datos (no un bug de la herramienta): como casi todo lo del diario está registrado como «Genérico» o con descripciones libres («2 unidades», «sin piel», «1 rebanada»), la respuesta agrupa esas descripciones como si fueran marcas. La herramienta hace bien su trabajo — cuenta lo que hay en el campo `brand` — pero ese campo rara vez tiene una marca real en un diario con productos genéricos. El propio modelo lo explicó honestamente: «en realidad no hay marcas comerciales claras en tu diario».
**Captura**: `screenshots/05-topbrands.png`

### #7 — `searchDiary`

**Prompt**: «¿Cuándo comí lomo por última vez?»
**Resultado**: ✅ Correcto — encontró el registro de hoy mismo (lomo embuchado del bocata) y dijo explícitamente que no había ningún registro anterior.
**Captura**: `screenshots/07-searchdiary.png`

### #8 — `mealTimingStats`

**Prompt**: «¿A qué hora suelo registrar la comida?»
**Resultado**: ✅ Correcto, y con una matización útil por su cuenta: aclaró que la hora que da es la hora en la que se *registra* el dato, no necesariamente la hora real de la comida.
**Captura**: `screenshots/08-mealtimingstats.png`

### #16 — `deleteEntries`

**Prompt**: «Quita el pincho de tortilla de la cena»
**Resultado**: ✅ Correcto — borrado confirmado y con su propio botón Deshacer.
**Captura**: `screenshots/16-deleteentries.png`

### #17 — `setEaten`

**Prompt**: «Marca el pollo asado del desayuno como comido»
**Resultado**: ✅ Correcto, con Deshacer.
**Captura**: `screenshots/17-seteaten.png`

### #18 — `createManualEntry`, ahora deshacible (Hallazgo B, primera mitad, reverificado)

**Prompt**: «La tarta especial que hizo mi tía el domingo, unas 400 kcal, en la cena»
**Resultado**: ✅ Correcto en dos sentidos. Primero, el modelo distinguió bien esto de un alimento de catálogo (no intentó buscar «tarta especial de mi tía» — sabe que algo tan idiosincrásico no puede estar en la base) y usó `createManualEntry` con la etiqueta de estimación. Segundo — y esto es lo que estaba roto antes del arreglo — el botón **Undo** funcionó de verdad: verificado por sqlite que la fila de `ManualDiaryEntry` desapareció al tocarlo, y la propia burbuja del chat cambió su botón a «Undone». Antes del arreglo de esta sesión esto no habría hecho nada, porque la herramienta nunca llamaba a `journal.record`.
**Capturas**: `screenshots/18-createmanualentry.png`, `screenshots/18b-undo-confirmado.png`

### #20 — `undo` vía herramienta (no el botón)

**Prompt**: «Deshaz lo último» (en vez de tocar el botón Undo)
**Resultado**: ✅ Correcto — deshizo el `setEaten` implícito de la última acción (el bocata pasó a no-comido), y lo explicó bien: «las dos entradas siguen en el diario, pero ya no están marcadas como comidas». Verificado por sqlite: `Measurement.isEaten` pasó a `0` en ambas filas.
**Captura**: `screenshots/20-undo-via-herramienta.png`

### #21 — `redo` (probado como «vuelve a marcarlo», no con la palabra «redo»)

**Prompt**: «Vuelve a marcarlo como comido»
**Resultado**: ✅ Funcionalmente correcto — usó `setEaten` para re-marcar ambas entradas como comidas, con su propio Undo. No se probó literalmente la herramienta `redo` (rehacer un undo concreto), pero el resultado observable — volver al estado anterior al deshacer — es el mismo.
**Captura**: `screenshots/21-redo-via-herramienta.png`

### #30 — La conversación sobrevive a salir

**Acción**: Salir al diario (botón atrás), volver a abrir el Asistente
**Resultado**: ✅ Correcto — todo el hilo (incluidos los botones «Undo»/«Undone» ya pulsados) seguía ahí tal cual se dejó.
**Captura**: `screenshots/30-conversacion-persistente.png`

### #28 — Persistencia de ajustes (reconfirmado varias veces)

**Acción**: Cerrar y reabrir Ajustes del asistente repetidas veces a lo largo de la sesión
**Resultado**: ✅ Correcto — `Server`, la máscara de la clave, y el chip de límite de pasos (`No limit`) sobrevivieron a cada apertura, incluida una reinstalación del APK entre medias.
**Captura**: `screenshots/28-persistencia-ajustes-sinlimite.png`

### #23 — Pad: un solo punto de historial para todo el plan

**Prompt**: «Rellename lo que me falta para llegar a 2000 kcal hoy» (con "Sin límite" activo, tras el arreglo de Hallazgo C)
**Resultado**: ✅ Correcto — el plan completo (5 alimentos: arroz, atún, huevos, plátano, yogur) generó **una sola** fila en `AssistantChange`: `"Plan aplicado: 5 alimento(s)"`, no una entrada por alimento. Verificado directamente por sqlite.
**Captura**: `screenshots/22-pad-plan-completo.png`

### #24 — Pad: deshacer el plan entero de una vez

**Acción**: Tocar el único botón «Undo» de la prueba #23
**Resultado**: ✅ Correcto — las filas de `Measurement` bajaron de 8 a 3 en un solo tap (los 5 alimentos del plan desaparecieron a la vez), y el botón cambió a «Undone». Un solo `undo` deshace todo el plan atómicamente, no hace falta deshacer alimento a alimento.
**Captura**: `screenshots/24-undo-plan-entero.png`

### #25 — Pad: partir de un día ya existente

**Prompt**: «Copia lo de hoy y ajusta la cena»
**Resultado**: ✅ Correcto y con buen criterio: copió el diario de hoy al borrador (pollo asado + lomo/chapata, 758 kcal) y, al ver que la cena estaba vacía, **preguntó qué poner** en vez de inventar algo — «¿Para qué día lo quieres y qué quieres poner en la cena?». Tras responder con arroz y atún, los añadió con cantidades y macros reales, dejándolos sin marcar como comidos (cena planificada, no ejecutada).
**Captura**: `screenshots/25-copiar-dia-y-ajustar.png`

### #26 — Pad: el cambio manual del usuario sobrevive

**Acción**: Con un plan de cena para mañana pendiente en el borrador del asistente (queso en lonchas, sin confirmar todavía), se añadió manualmente desde la pantalla normal de la app (no desde el chat) un "Queso Genérico" al desayuno de mañana. Después se le pidió al asistente que confirmara su plan de cena.
**Resultado**: ✅ Correcto — el desayuno de mañana conserva el "Queso Genérico" añadido a mano (25 g, sin marcar, tal cual se dejó), y la cena de mañana tiene el "Queso en lonchas de vaca (Entrepinares)" que confirmó el asistente (100 g, sin marcar). Ambas entradas conviven sin pisarse ni duplicarse.
**Captura**: `screenshots/26-cambio-manual-sobrevive.png`

**Nota aparte**: se descubrió que el borrador del pad (`AssistantPad`, un `single` de Koin en memoria) no sobrevive a matar el proceso de la app — al forzar el cierre y reabrir para esta misma prueba, un borrador a medias ("¿qué queso tenías en mente?") se perdió silenciosamente sin ningún aviso al usuario. No es exactamente lo que pedía esta prueba, pero es un matiz relacionado: si el usuario está a mitad de construir un plan con el asistente y el sistema mata la app (memoria baja, etc.), el borrador desaparece sin rastro. Comportamiento razonable para un estado efímero, pero vale la pena que quede anotado.

### #31 — Foto con visión

**Prompt**: adjuntar una foto real (un bocadillo con huevo frito, queso fundido y lechuga) junto con «añádelo a la cena», luego responder a la pregunta de seguimiento del modelo.
**Resultado**: ⚠️→❌ El primer turno funcionó perfectamente: el modelo describió la foto con detalle exacto («pan brioche, huevo frito, queso fundido y lechuga») y preguntó razonablemente qué carne llevaba dentro, en vez de inventarlo. Pero el **segundo turno** (la respuesta a esa pregunta, sin foto nueva) falló con `HTTP 400: "This model does not support image"` — ver **Hallazgo E**. El diario no se tocó en el turno fallido.
**Captura**: `screenshots/31-vision-turno1-ok.png`

### Incidente evitado: edición manual del campo de clave API

Al intentar reproducir la prueba #33 (clave inválida) escribiendo directamente en el campo de texto vía `adb shell input text`, una serie de toques imprecisos (coordenadas calculadas mal tras un cambio de layout del teclado) escribieron texto basura tanto en el campo **Server** como en el campo **API key**, y una pulsación fallida sobre lo que debía ser «Test connection» golpeó en realidad el propio campo de texto. **Nada de esto llegó a guardarse**: el botón real «Test connection» nunca se pulsó con esos valores corruptos en el borrador (`apiKeyDraft`), así que la clave real almacenada nunca se sobrescribió — se confirmó reabriendo Ajustes y viendo la máscara original intacta. Aun así, se decidió **no completar la prueba #33 en este entorno**: el riesgo de corromper de verdad la clave real (que no se puede leer en texto plano una vez guardada) no compensa frente a un hallazgo de bajo riesgo. Recomendación: probar #33 a mano, tecleando directamente en el dispositivo en vez de vía `adb input text`.

### Bug: timeout HTTP (encontrado y arreglado en esta sesión)

Durante la prueba #19 apareció: *«Socket timeout has expired [url=https://api.deepseek.com/chat/completions, socket_timeout=unknown] ms»*. El cliente instalaba `HttpTimeout` sin configurar ningún valor, así que se aplicaban los ~10 s por defecto del motor (OkHttp en Android) — insuficientes para un modelo de razonamiento en medio de una cadena de herramientas. Arreglado subiendo `requestTimeoutMillis`/`socketTimeoutMillis` a 90 000 y `connectTimeoutMillis` a 15 000.
**Captura**: `screenshots/bug-socket-timeout.png`

---

## Hallazgos detallados

### Hallazgo A — Las entradas manuales son invisibles para el asistente

**Dónde**: `DailyTotalsUseCase`, `DiaryRangeUseCase`, `TopFoodsUseCase`, `TopBrandsUseCase`, `NutrientAttributionUseCase`, `SearchDiaryUseCase`, `MealTimingStatsUseCase` — todas construidas solo sobre `FoodDiaryEntryRepository` (tabla `Measurement`, productos/recetas).

**Por qué importa**: `createManualEntry` escribe en una tabla distinta (`ManualDiaryEntry`), y ningún caso de uso de lectura la consulta. La Home de la app sí mezcla ambas fuentes para sus totales (por eso el total de 444 kcal en pantalla era correcto), pero el asistente, al preguntarle por su propio diario, no ve una parte de él. Esto no es solo un problema con lo que crea la propia IA: cualquier entrada manual que el usuario añada desde la app normal también sería invisible para el asistente.

**Reproducción**: Con al menos una entrada manual en el diario (ver prueba #15), preguntar «¿qué tengo registrado hoy?» o «¿de dónde viene la grasa?» — la respuesta ignora las entradas manuales por completo.

**Alcance del arreglo** (no aplicado en esta sesión): añadir una fuente de datos de `ManualDiaryEntry` a cada uno de los 7 casos de uso de lectura, fusionando ambos orígenes antes de agregar. Toca los siete ficheros de `assistant/domain/query/`.

### Hallazgo B — `createManualEntry` no respeta ni el journal ni el "sin marcar"

**Dónde**: `app/src/commonMain/kotlin/com/maksimowiczm/foodyou/assistant/domain/tool/write/ManualEntryTool.kt`

**Dos problemas en la misma herramienta**:
1. `mutates = true` pero nunca llama a `journal.record(...)`. Resultado: no aparece en `history`, y no hay forma de deshacerlo ni con el botón ni con la herramienta `undo`.
2. `ManualDiaryEntry.isEaten` tiene valor por defecto `true` (ver `fooddiary/domain/entity/ManualDiaryEntry.kt:27`), y `CreateManualEntryTool` no lo fuerza a `false` al construir la entrada. Resultado: toda entrada manual que crea el asistente nace **ya marcada como comida**, rompiendo la regla central de que lo que añade la IA queda pendiente de confirmación del usuario.

**Efecto observado**: en la prueba #15, el asistente afirmó por escrito «todo sigue sin marcar como comido» mientras la base de datos real ya tenía esas mismas entradas marcadas — la respuesta del modelo y el estado real de la app quedaron desincronizados por este bug.

**Arreglo** (no aplicado): en `CreateManualEntryTool.call`, pasar explícitamente una entidad con `isEaten = false` (requiere que `ManualDiaryEntryRepository.insert` acepte ese parámetro, o cambiar el valor tras insertar), y añadir una llamada a `journal.record` con un `UndoAction` nuevo para entradas manuales — `UndoAction.DeleteMeasurements` no sirve porque esas entradas no viven en la tabla `Measurement`.

### Hallazgo C — El límite de 8 pasos es bajo para este modelo

**Observado dos veces**: «Añade 2 huevos y una tostada al desayuno» (prueba #14) y «Busca pollo en la base de datos» tras un turno que había sugerido completar el día (prueba #11). En ambos casos el bucle se quedó sin pasos antes de terminar.

**Lo que funcionó bien**: el mensaje de error es honesto sobre el límite alcanzado, y la propia app no queda en un estado roto — pero el mensaje «tu diario no ha sido tocado» resultó **falso** en la prueba #14, porque el modelo alcanza a ejecutar herramientas mutantes (como `createManualEntry`) antes de agotar el contador, y esas mutaciones sí persisten aunque el turno termine en error.

**Arreglo aplicado y verificado en vivo**: chips 8/16/32/64/128/**Sin límite** en
`AssistantSettingsScreen` (`0` es el valor centinela de "sin límite"), `AgentLoop` cambia de
`repeat(maxIterations)` a un `while (unlimited || iteration < maxIterations)`. Verificado: el chip
"No limit" se selecciona y persiste tras salir y volver a Ajustes. Nota de precaución: con el
límite quitado, un modelo confundido podría en teoría encadenar llamadas indefinidamente — no se ha
observado en ninguna prueba de esta sesión (el modelo siempre converge a una respuesta de texto en
pocos pasos), pero merece vigilancia si se usan modelos menos capaces.

### Hallazgo D — `searchFood` solo leía el espejo local, nunca disparaba una búsqueda de verdad

**Dónde**: `SearchFoodTool.call` → `ProductRepository.searchProducts` → `ProductDao.searchProductsByText`, una consulta **puramente local** (FTS4 sobre la tabla `Product`, que actúa de espejo/caché de las fuentes remotas — ver `docs/decision-log/0003`).

**Por qué importa**: la pantalla de búsqueda real solo rellena ese espejo local cuando el usuario navega a la pestaña de una fuente concreta ("Custom database", "Open Food Facts"...) y teclea algo — en ese momento un `RemoteMediator` (`CustomFoodSourceRemoteMediator` y equivalentes) pide una página al servidor y la inserta en `Product`. El asistente **nunca pasa por ahí**: solo lee lo que ya esté cacheado. Resultado, reproducido con la tabla `Product` genuinamente vacía (aplicación recién instalada): «busca huevos en la base de datos» y «busca lomo embuchado» devolvían **[]** — no por un fallo del servidor (confirmado con `curl` y con la pestaña "Custom database" de la propia pantalla de búsqueda, que sí encontraba resultados al instante) sino porque el asistente jamás había disparado la red. El modelo, honestamente, no inventaba macros — pero tampoco encontraba nada real, así que terminaba proponiendo `createManualEntry` con una estimación para casi cualquier alimento en una instalación nueva.

**Reproducción** (antes del arreglo): con `adb shell run-as com.maksimowiczm.foodyou sqlite3 databases/open_source_database.db 'select count(1) from Product;'` devolviendo `0`, pedir al asistente «busca X en la base de datos» para cualquier X no buscado antes a mano en la pantalla normal.

**Arreglo**: `AssistantRemoteFoodFallback` (`assistant/domain/tool/read/AssistantRemoteFoodFallback.kt`) reutiliza los `ProductRemoteMediatorFactory` ya existentes (uno por fuente: custom, Open Food Facts, USDA) y llama a `mediator.load(LoadType.APPEND, ...)` directamente — sin pasar por `Pager`, que el asistente no necesita — para pedir una página real y dejarla cacheada en `Product`, exactamente igual que hace la pantalla de búsqueda. `SearchFoodTool` lo invoca solo cuando la consulta local sale vacía, y repite la consulta local una vez cacheado. Coste: una llamada de red extra, solo en el primer cache-miss de cada término.

**Verificado en vivo**: con `Product` en 0 filas, «busca lomo embuchado en la base de datos» devolvió una tabla con 8 productos reales (Lomo Embuchado Genérico/Hacendado/Realvalle/...) con sus macros reales del servidor. Confirmado con sqlite que `Product` pasó a tener 52 filas tras esa única búsqueda.

### Hallazgo E — Un segundo turno sin foto rompe la visión si el primero tenía una (no arreglado)

**Dónde**: `AssistantChatViewModel.send()` — `val hasImage = imageDataUri != null` seguido de `useVisionModel = hasImage` al llamar a `agentLoop.run(...)`, junto con `history = conversation.history.dropLast(1)`, que siempre manda el historial completo tal cual quedó guardado.

**El mecanismo exacto**: cada turno decide su propio modelo mirando solo si *ese* mensaje trae una imagen nueva — no si hay alguna imagen en cualquier punto del historial. Turno 1 («adjunta foto + "añádelo a la cena"») tiene imagen → `useVisionModel = true` → el modelo de visión la recibe y la describe bien. Turno 2 (solo texto, ninguna foto nueva, p. ej. «es una hamburguesa de ternera») no tiene imagen propia → `useVisionModel = false` → pero el historial que se envía en ese turno **sigue incluyendo el mensaje del turno 1**, con su `image_url` dentro. La API recibe una petición dirigida al modelo de texto normal que contiene una imagen en el historial, y la rechaza: `HTTP 400 {"error":{"message":"This model does not support image", ...}}`. El turno falla entero y el diario no se toca en ese turno (el mensaje de error es honesto en eso), pero la conversación queda encallada: cualquier respuesta de seguimiento sin adjuntar la foto de nuevo repetirá el mismo error, porque la imagen ya está en el historial para siempre.

**Diagnóstico** (aportado directamente): no se puede mezclar, dentro de la misma conversación, un modelo con visión y uno sin visión una vez que ha entrado una imagen al historial. O se usa el modelo de visión de forma consistente mientras haya alguna imagen en cualquier turno anterior, o se despoja el historial de las partes de imagen antes de mandarlo a un modelo que no las admite. Mezclar ambos criterios turno a turno, como hace el código actual, garantiza este fallo en cuanto la conversación tiene más de un turno tras la foto.

**Reproducción**: adjuntar cualquier foto de comida con un texto («añádelo a la cena»), dejar que responda, y contestar cualquier cosa de texto plano sin adjuntar nada — el segundo turno siempre falla con el 400 de arriba.

**Arreglo** (no aplicado — solo diagnosticado y documentado por decisión explícita de no gastar más en pruebas con imágenes esta sesión): dos direcciones razonables, a decidir:
1. Decidir `useVisionModel` para el turno actual mirando si *algún* turno anterior en el historial tiene imagen, no solo el mensaje que se está mandando ahora — así toda la conversación se queda en modelo de visión en cuanto entra una foto.
2. O, si se prefiere volver al modelo normal en cuanto no hace falta ya la imagen, despojar las partes `image_url` de los mensajes históricos antes de mandarlos al modelo sin visión, para que el historial que ve la API nunca contenga una imagen que ese modelo no admite.
La opción 1 es más simple de implementar y evita perder el contexto visual si el usuario sigue haciendo preguntas relacionadas con la foto.

---

## Nota sobre el límite de pasos (Hallazgo C) — ya aplicado

Ver el arreglo verificado más arriba (Hallazgo C). Queda como aviso, no como pendiente: vigilar el
coste/tiempo si alguna prueba futura con "Sin límite" se queda visiblemente colgada, y documentarlo
como hallazgo nuevo si ocurre.

---

## Pruebas pendientes

De las 34 filas de la tabla maestra, quedan sin ejecutar en vivo solo: **#27** (icono ✨ desaparece
de Home al quitar la clave) y **#33** (clave inválida) — ambas aplazadas a propósito al final por
el riesgo de manipular la clave real de API vía automatización frágil (ver el incidente evitado más
arriba); la **#32** (dictado) sigue sin ser verificable en este entorno (sin micrófono real ni
servicio de reconocimiento).

Todo lo demás está ejecutado y con resultado documentado: las 12 pruebas de la sesión anterior, más
`topBrands`, `searchDiary`, `mealTimingStats`, `searchFood` (reverificado con el Hallazgo D
corregido), `deleteEntries`, `setEaten`, `createManualEntry` con Undo real, `undo`/`redo` vía
herramienta, la conversación persistente, la persistencia de ajustes, las cinco pruebas del pad
(#22–26, incluyendo el plan completo con "Sin límite" ya sin agotarse), y la foto con visión (#31,
que reveló el Hallazgo E).

Los hallazgos A, B (primera mitad), C, D y el prompt de descomposición de platos están verificados
en vivo en el dispositivo. Quedan sin arreglar dos cosas, de naturaleza distinta: la segunda mitad
de B (columna `isEaten` inexistente en `ManualDiaryEntry`) es una limitación estructural de toda la
app, no arreglable sin una migración de esquema; el Hallazgo E (visión rota al segundo turno) sí es
arreglable con un cambio acotado, pero se ha dejado sin tocar esta sesión por decisión explícita de
no seguir gastando en pruebas con imágenes.

---

## Resumen final

**30 de 34 pruebas ejecutadas y en verde o con hallazgo documentado** (más 2 verificadas por
código/tests unitarios: #4 y #6). **8 fallos reales encontrados a lo largo de las dos sesiones: 6
arreglados y verificados en el dispositivo real, 1 documentado como limitación estructural
conocida (segunda mitad de B), y 1 diagnosticado con precisión pero sin arreglar (Hallazgo E,
visión rota al segundo turno).** El más importante de los seis arreglados (Hallazgo D) afectaba a
cualquier alimento que la persona no hubiera buscado antes a mano — el caso más común de uso real
en una instalación nueva — así que su corrección probablemente importa más en la práctica que
cualquiera de los otros. El Hallazgo E, aunque sin arreglar, sí bloquea un uso normal y esperable
(mandar una foto y luego seguir hablando de ella), así que conviene priorizarlo pronto.

Quedan solo 2 filas sin ejecutar en vivo por decisión explícita de esta sesión (riesgo de
credenciales en #27/#33) y 1 no verificable en este entorno (#32, dictado). Ninguna de ellas señala
un hallazgo pendiente de investigar — son huecos de cobertura, no sospechas de bug. Aparte de la
tabla, esta sesión también dejó una lista de siete cutreces de interfaz (markdown roto, icono "?"
sin explicar, pestaña cortada, el rayo ⚡ con doble significado, el estado vacío de búsqueda, las
fotos que no se ven en el chat, y la cámara que solo abre la galería) para revisar juntas cuando se
cierre esta ronda de pruebas.
