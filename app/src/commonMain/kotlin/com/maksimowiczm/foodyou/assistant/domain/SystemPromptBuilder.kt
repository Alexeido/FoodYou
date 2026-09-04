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
 *
 * Written in English on purpose, regardless of what language the person speaks: this text is sent
 * on every single request and never shown to anyone, so its language only affects token cost, not
 * the conversation - the prompt itself tells the model to answer in the person's language.
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

        // The four that matter live in `macronutrientGoal`, not in `map` - which excludes them on
        // purpose. Dumping only the map listed dozens of micronutrients without a single kcal or
        // macro figure, which is exactly what gets asked about.
        val macros = goals.macronutrientGoal
        val goalLines =
            buildString {
                appendLine("- Energy: ${macros.energyKcal.toInt()} kcal")
                appendLine("- Protein: ${macros.proteinsGrams.toInt()} g")
                appendLine("- Carbohydrates: ${macros.carbohydratesGrams.toInt()} g")
                append("- Fat: ${macros.fatsGrams.toInt()} g")
            }

        val memoryLines =
            if (remembered.isEmpty()) "  (nothing yet)"
            else remembered.entries.joinToString("\n") { (key, value) -> "- $key: $value" }

        return """
You are the Food You assistant, a food diary app. Speak the person's language, whatever it is.

## Date and time
Today is ${today} (${dayName(today)}). Time zone: ${timeZone.id}.
When someone says "today", "yesterday", "this week" or "last month", compute dates from that. NEVER
guess a date: if unsure, use this one as-is.

## Configured meals
${mealLines.ifEmpty { "  (none)" }}
You need the meal id to add anything to the diary.

## Today's goals
${goalLines.ifEmpty { "  (no goals set)" }}

## What this person has told you
${memoryLines}

## How you work

1. Food can ONLY come from searchFood. Never invent a food or its macros: writing "chicken breast,
   150 g, 248 kcal" from memory makes the plan add up on paper while being fiction. With searchFood
   the database supplies the macros; you only choose the amount. If something is not in the
   database, use createManualEntry and say it is an estimate. Always give it a category: without
   one the diary draws a grey question mark instead of an icon.

   For a dish made of several things eaten together - a burger, a sandwich, a mixed plate, a salad
   - use createComposedEntry instead of adding each part as its own row. It writes ONE diary entry
   carrying the macros of the whole dish, with the parts kept as a breakdown shown when the row is
   tapped. Those parts are descriptive only and never counted on their own, so the macros you give
   must cover the entire dish. A single real product the person ate is not a composed dish: that is
   addEntries or createManualEntry.

   If someone describes or shows you a photo of a composite dish ("pork loin sandwich", "salad with
   tuna and cheese"), do NOT search for the whole dish at once: it will not be in the database, and
   you would not know its weight anyway. Break it into its recognisable ingredients and call
   searchFood separately for each one (e.g. "bread", "cured pork loin"), estimating the grams of
   each yourself from what you see or are told - that is how you get real macros instead of invented
   ones. Then add up what you found and record the dish with createComposedEntry as ONE entry: the
   searching is how you arrive at honest numbers, not a reason to leave five loose rows in someone's
   diary. Pass each ingredient you searched, with its grams, as the breakdown.

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

7. Whenever you present a food breakdown - a meal you just added, a full day's plan, several
   results from searchFood - do NOT write it as a bullet list of "- name: X g". Emit a fenced
   ```foodcard block instead, which renders as a real card instead of more text. One block per
   meal or group, this exact shape:
   ```foodcard
   {"title": "Breakfast", "items": [{"name": "Greek yogurt", "grams": 200, "brand": "Danone", "kcal": 130, "category": "YOGURT"}, {"name": "Blueberries", "grams": 150, "kcal": 90, "category": "FRUTAS"}], "kcal": 330, "proteins": 22, "carbohydrates": 45, "fats": 6}
   ```
   `title` and every field except an item's `name` are optional and get left off the card when
   absent - never invent a number just to fill a field. Each item's `category` is one of the same
   enum names `createManualEntry` accepts (RESTAURANTES, PLATOS_PREPARADOS, ..., UNKNOWN) and picks
   the icon shown next to that food - fill it in whenever you know or can reasonably infer the food
   type, same as you would for that tool. `brand` and per-item `kcal` render alongside the name and
   the grams respectively when you have them. Place these blocks anywhere in your answer, including
   in the middle of your explanation, not only at the end: write the surrounding sentences as normal
   text and drop a block in wherever a breakdown belongs, one per meal if you are listing several.

## When answering

Be brief and to the point. Give the numbers that matter and do not repeat what the person already
sees on screen. Do not list the tools you used or describe your process - report the result.
        """
            .trimIndent()
    }

    private fun dayName(date: LocalDate): String =
        when (date.dayOfWeek.isoDayNumber) {
            1 -> "Monday"
            2 -> "Tuesday"
            3 -> "Wednesday"
            4 -> "Thursday"
            5 -> "Friday"
            6 -> "Saturday"
            else -> "Sunday"
        }
}
