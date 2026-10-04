package com.maksimowiczm.foodyou.goals.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.domain.food.NutritionFactsField
import com.maksimowiczm.foodyou.goals.domain.entity.DailyGoal
import com.maksimowiczm.foodyou.goals.domain.entity.MacronutrientGoal
import com.maksimowiczm.foodyou.goals.domain.entity.WeeklyGoals
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.sync.domain.SyncedGoals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

internal class DataStoreGoalsRepository(private val dataStore: DataStore<Preferences>) :
    GoalsRepository {
    override suspend fun updateWeeklyGoals(weeklyGoals: WeeklyGoals) {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                val serialized = Json.encodeToString(DataStoreWeeklyGoals(weeklyGoals))
                set(GoalsDataStoreKeys.weeklyGoals, serialized)
            }
        }
    }

    override fun observeWeeklyGoals(): Flow<WeeklyGoals> =
        dataStore.data.map { preferences ->
            preferences[GoalsDataStoreKeys.weeklyGoals]?.let { serialized ->
                Json.decodeFromString<DataStoreWeeklyGoals>(serialized).toWeeklyGoals()
            } ?: WeeklyGoals.defaultGoals
        }

    override fun observeTrackedNutrients(): Flow<List<NutritionFactsField>> =
        dataStore.data.map { decodeTracked(it[GoalsDataStoreKeys.trackedNutrients]) }

    override suspend fun setTrackedNutrients(fields: List<NutritionFactsField>) {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                set(GoalsDataStoreKeys.trackedNutrients, encodeTracked(fields))
            }
        }
    }

    override fun observeDailyGoals(date: LocalDate): Flow<DailyGoal> =
        observeWeeklyGoals().map {
            when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> it.monday
                DayOfWeek.TUESDAY -> it.tuesday
                DayOfWeek.WEDNESDAY -> it.wednesday
                DayOfWeek.THURSDAY -> it.thursday
                DayOfWeek.FRIDAY -> it.friday
                DayOfWeek.SATURDAY -> it.saturday
                DayOfWeek.SUNDAY -> it.sunday
            }
        }
}

private object GoalsDataStoreKeys {
    val weeklyGoals = stringPreferencesKey("fooddiary:weekly_goals_2")
    val trackedNutrients = stringPreferencesKey("goals:tracked_nutrients")
}

/** Comma-separated field names; unknown ones (from a newer version) are skipped. */
private fun decodeTracked(value: String?): List<NutritionFactsField> =
    value
        .orEmpty()
        .split(',')
        .mapNotNull { name -> NutritionFactsField.entries.firstOrNull { it.name == name.trim() } }
        .filter { it !in macroFields }
        .distinct()

private fun encodeTracked(fields: List<NutritionFactsField>): String =
    fields.filter { it !in macroFields }.distinct().joinToString(",") { it.name }

private val macroFields =
        setOf(
            NutritionFactsField.Energy,
            NutritionFactsField.Proteins,
            NutritionFactsField.Fats,
            NutritionFactsField.Carbohydrates,
        )

/**
 * The goals as one sync document (docs/sync/protocol.md, kind `goals`): the same shape the app
 * stores them in, so every device of the account - and the MCP - works with the same goals.
 *
 * - `separateDays`: whether each weekday has its own goals.
 * - `days`: `{"monday": {"map": {"Energy": 2000, "Proteins": 0.2, ...}, "isDistribution": true}, ...}`
 *   in grams; with `isDistribution`, Proteins/Fats/Carbohydrates are shares of the energy.
 * - `tracked`: the nutrients the person wants to reach, e.g. `["Calcium"]`.
 */
internal class GoalsSyncAdapter(private val dataStore: DataStore<Preferences>) :
    SyncedGoals {

    private val json = Json { encodeDefaults = true }

    override val changes: Flow<Any> =
        dataStore.data
            .map { it[GoalsDataStoreKeys.weeklyGoals] to it[GoalsDataStoreKeys.trackedNutrients] }
            .distinctUntilChanged()

    override suspend fun read(): Map<String, JsonElement> = encode(dataStore.data.first())

    override suspend fun apply(fields: Map<String, JsonElement>) {
        dataStore.updateData { preferences ->
            val current = encode(preferences)
            val separate = fields[SEPARATE] ?: current.getValue(SEPARATE)
            val days = fields[DAYS] as? JsonObject ?: current.getValue(DAYS) as JsonObject
            val weekly =
                runCatching {
                        json
                            .decodeFromJsonElement<DataStoreWeeklyGoals>(
                                JsonObject(days + ("useSeparateGoals" to separate))
                            )
                            .also { it.toWeeklyGoals() } // that it makes sense, or keep ours
                    }
                    .getOrNull()
            val tracked =
                (fields[TRACKED] as? JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    ?.joinToString(",")
            preferences.toMutablePreferences().apply {
                weekly?.let { set(GoalsDataStoreKeys.weeklyGoals, json.encodeToString(it)) }
                tracked?.let { set(GoalsDataStoreKeys.trackedNutrients, encodeTracked(decodeTracked(it))) }
            }
        }
    }

    private fun encode(preferences: Preferences): Map<String, JsonElement> {
        val weekly =
            preferences[GoalsDataStoreKeys.weeklyGoals]?.let {
                runCatching { json.decodeFromString<DataStoreWeeklyGoals>(it) }.getOrNull()
            } ?: DataStoreWeeklyGoals(WeeklyGoals.defaultGoals)
        val obj = json.encodeToJsonElement(weekly).jsonObject
        return mapOf(
            SEPARATE to obj.getValue("useSeparateGoals"),
            DAYS to JsonObject(obj - "useSeparateGoals"),
            TRACKED to
                JsonArray(
                    decodeTracked(preferences[GoalsDataStoreKeys.trackedNutrients]).map {
                        JsonPrimitive(it.name)
                    }
                ),
        )
    }

    private companion object {
        const val SEPARATE = "separateDays"
        const val DAYS = "days"
        const val TRACKED = "tracked"
    }
}

@Serializable
private class DataStoreWeeklyGoals(
    val useSeparateGoals: Boolean,
    val monday: DataStoreDailyGoal,
    val tuesday: DataStoreDailyGoal,
    val wednesday: DataStoreDailyGoal,
    val thursday: DataStoreDailyGoal,
    val friday: DataStoreDailyGoal,
    val saturday: DataStoreDailyGoal,
    val sunday: DataStoreDailyGoal,
) {
    constructor(
        weeklyGoals: WeeklyGoals
    ) : this(
        useSeparateGoals = weeklyGoals.useSeparateGoals,
        monday = weeklyGoals.monday.intoDataStoreDailyGoal(),
        tuesday = weeklyGoals.tuesday.intoDataStoreDailyGoal(),
        wednesday = weeklyGoals.wednesday.intoDataStoreDailyGoal(),
        thursday = weeklyGoals.thursday.intoDataStoreDailyGoal(),
        friday = weeklyGoals.friday.intoDataStoreDailyGoal(),
        saturday = weeklyGoals.saturday.intoDataStoreDailyGoal(),
        sunday = weeklyGoals.sunday.intoDataStoreDailyGoal(),
    )

    fun toWeeklyGoals(): WeeklyGoals =
        WeeklyGoals(
            useSeparateGoals = useSeparateGoals,
            monday = monday.toDailyGoal(),
            tuesday = tuesday.toDailyGoal(),
            wednesday = wednesday.toDailyGoal(),
            thursday = thursday.toDailyGoal(),
            friday = friday.toDailyGoal(),
            saturday = saturday.toDailyGoal(),
            sunday = sunday.toDailyGoal(),
        )
}

@Serializable
private class DataStoreDailyGoal(
    val map: Map<NutritionFactsField, Double>,
    val isDistribution: Boolean,
) {
    fun toDailyGoal(): DailyGoal {
        val macronutrientGoal =
            if (isDistribution) {
                MacronutrientGoal.Distribution(
                    energyKcal =
                        map[NutritionFactsField.Energy]
                            ?: error("Energy must be set for distribution goal"),
                    proteinsPercentage =
                        map[NutritionFactsField.Proteins]
                            ?: error("Proteins must be set for distribution goal"),
                    fatsPercentage =
                        map[NutritionFactsField.Fats]
                            ?: error("Fats must be set for distribution goal"),
                    carbohydratesPercentage =
                        map[NutritionFactsField.Carbohydrates]
                            ?: error("Carbohydrates must be set for distribution goal"),
                )
            } else {
                MacronutrientGoal.Manual(
                    energyKcal =
                        map[NutritionFactsField.Energy]
                            ?: error("Energy must be set for manual goal"),
                    proteinsGrams =
                        map[NutritionFactsField.Proteins]
                            ?: error("Proteins must be set for manual goal"),
                    fatsGrams =
                        map[NutritionFactsField.Fats] ?: error("Fats must be set for manual goal"),
                    carbohydratesGrams =
                        map[NutritionFactsField.Carbohydrates]
                            ?: error("Carbohydrates must be set for manual goal"),
                )
            }

        return DailyGoal(macronutrientGoal = macronutrientGoal, map = map)
    }
}

private fun DailyGoal.intoDataStoreDailyGoal(): DataStoreDailyGoal {
    val macronutrientGoal = macronutrientGoal
    val energy = macronutrientGoal.energyKcal

    val proteins =
        when (macronutrientGoal) {
            is MacronutrientGoal.Manual -> macronutrientGoal.proteinsGrams
            is MacronutrientGoal.Distribution -> macronutrientGoal.proteinsPercentage
        }

    val fats =
        when (macronutrientGoal) {
            is MacronutrientGoal.Manual -> macronutrientGoal.fatsGrams
            is MacronutrientGoal.Distribution -> macronutrientGoal.fatsPercentage
        }

    val carbohydrates =
        when (macronutrientGoal) {
            is MacronutrientGoal.Manual -> macronutrientGoal.carbohydratesGrams
            is MacronutrientGoal.Distribution -> macronutrientGoal.carbohydratesPercentage
        }

    val newMap =
        map.toMutableMap().apply {
            this[NutritionFactsField.Energy] = energy
            this[NutritionFactsField.Proteins] = proteins
            this[NutritionFactsField.Fats] = fats
            this[NutritionFactsField.Carbohydrates] = carbohydrates
        }

    return DataStoreDailyGoal(map = newMap, isDistribution = isDistribution)
}
