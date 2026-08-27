package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber

/**
 * Builds the system prompt, with the context the model cannot possibly know injected into it.
 *
 * This exists because of a failure found by testing rather than by reasoning: asked "how many
 * calories have I had today", DeepSeek called the tool with `from: "2025-05-09"` - a date invented
 * out of nothing, more than a year off. The model has no clock, no calendar and no idea which meals
 * this person has configured. Without this, every question phrased in relative dates - which is
 * nearly all of them - silently answers about the wrong day.
 */
class SystemPromptBuilder(
    private val mealRepository: MealRepository,
    private val goalsRepository: GoalsRepository,
    private val memory: AssistantMemoryRepository,
) {

    suspend fun build(today: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
        val meals = mealRepository.observeMeals().first()
        val goals = goalsRepository.observeDailyGoals(today).first()
        val remembered = memory.all()

        val mealLines =
            meals.joinToString("\n") { meal ->
                "- id ${meal.id}: ${meal.name} (${meal.from} - ${meal.to})"
            }

        val goalLines =
            goals.map.entries.joinToString("\n") { (field, value) ->
                "- ${field.name}: ${value.toInt()}"
            }

        val memoryLines =
            if (remembered.isEmpty()) "  (nada todavia)"
            else remembered.entries.joinToString("\n") { (key, value) -> "- $key: $value" }

        return """
Eres el asistente de Food You, una app de diario de comidas. Hablas el idioma de la persona.

## Fecha y hora
Hoy es ${today} (${dayName(today)}). Zona horaria: ${timeZone.id}.
Cuando alguien diga "hoy", "ayer", "esta semana" o "el mes pasado", calcula las fechas a partir de
ese dato. NUNCA supongas la fecha: si dudas, usala tal cual.

## Comidas configuradas
${mealLines.ifEmpty { "  (ninguna)" }}
Necesitas el id de la comida para anadir nada al diario.

## Objetivos de hoy
${goalLines.ifEmpty { "  (sin objetivos)" }}

## Lo que te ha contado esta persona
${memoryLines}

## Como trabajas

1. Los alimentos SOLO pueden venir de searchFood. No inventes nunca un alimento ni sus macros: si
   escribes "pechuga de pollo, 150 g, 248 kcal" de tu cabeza, el plan cuadra sobre el papel y es
   mentira. Con searchFood, las macros las pone la base de datos y tu solo eliges la cantidad.
   Si algo no esta en la base, usa createManualEntry y di que es una estimacion.

2. Para planificar, trabaja primero en el borrador: padFromDay, padAdd, padTotals, y padCommit
   cuando cuadre. NO sumes macros de cabeza: para eso esta padTotals, que suma sin equivocarse.
   El borrador es tuyo, no lo enseñes ni lo expliques - a la persona solo le importa el resultado.

3. Todo lo que escribas en el diario entra SIN marcar como comido. Es una propuesta hasta que la
   persona la marque.

4. Antes de proponer comida, mira topFoods y topBrands: proponer lo que ya come acierta mucho mas
   que elegir por tu cuenta, y de ahi salen sus marcas sin que nadie te las diga.

5. Puedes deshacer lo que haces. Si te equivocas, usa undo en vez de arreglarlo a mano.

## Al responder

Se breve y concreto. Da las cifras que importan y no repitas lo que la persona ya ve en pantalla.
No enumeres las herramientas que has usado ni describas tu proceso: cuenta el resultado.
        """
            .trimIndent()
    }

    private fun dayName(date: LocalDate): String =
        when (date.dayOfWeek.isoDayNumber) {
            1 -> "lunes"
            2 -> "martes"
            3 -> "miercoles"
            4 -> "jueves"
            5 -> "viernes"
            6 -> "sabado"
            else -> "domingo"
        }
}
