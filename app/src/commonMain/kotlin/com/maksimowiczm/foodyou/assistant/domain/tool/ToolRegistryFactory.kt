package com.maksimowiczm.foodyou.assistant.domain.tool

import com.maksimowiczm.foodyou.assistant.domain.tool.read.DailyTotalsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.DiaryRangeTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.GoalsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.ListMealsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.MealTimingStatsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.NutrientAttributionTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.SearchDiaryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.SearchFoodTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.TopBrandsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.TopFoodsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.AddEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateRecipeTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateManualEntryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.DeleteEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.HistoryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.RedoTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.SetEatenTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.UndoTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.UpdateEntryTool

/**
 * Everything the model is allowed to do, in one place.
 *
 * Assembled here rather than in the DI module so the set is readable as a list: this is the surface
 * area of the assistant, and it should be possible to see all of it at once.
 */
class ToolRegistryFactory(
    private val dailyTotals: DailyTotalsTool,
    private val diaryRange: DiaryRangeTool,
    private val topFoods: TopFoodsTool,
    private val topBrands: TopBrandsTool,
    private val nutrientAttribution: NutrientAttributionTool,
    private val searchDiary: SearchDiaryTool,
    private val mealTimingStats: MealTimingStatsTool,
    private val listMeals: ListMealsTool,
    private val goals: GoalsTool,
    private val searchFood: SearchFoodTool,
    private val addEntries: AddEntriesTool,
    private val updateEntry: UpdateEntryTool,
    private val deleteEntries: DeleteEntriesTool,
    private val setEaten: SetEatenTool,
    private val createManualEntry: CreateManualEntryTool,
    private val createRecipe: CreateRecipeTool,
    private val history: HistoryTool,
    private val undo: UndoTool,
    private val redo: RedoTool,
) {
    fun create(): ToolRegistry =
        ToolRegistry(
            listOf(
                // Lectura
                dailyTotals,
                diaryRange,
                topFoods,
                topBrands,
                nutrientAttribution,
                searchDiary,
                mealTimingStats,
                listMeals,
                goals,
                searchFood,
                // Escritura
                addEntries,
                updateEntry,
                deleteEntries,
                setEaten,
                createManualEntry,
                createRecipe,
                // Historial
                history,
                undo,
                redo,
            )
        )
}
