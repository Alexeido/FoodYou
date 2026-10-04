package com.maksimowiczm.foodyou.app.ui.food.diary.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.common.result.onError
import com.maksimowiczm.foodyou.common.result.onSuccess
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.UnpackFoodDiaryEntryUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.UpdateFoodDiaryEntryUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class UpdateFoodDiaryEntryViewModel(
    private val entryId: FoodDiaryEntryId,
    private val updateFoodDiaryEntryUseCase: UpdateFoodDiaryEntryUseCase,
    private val unpackDiaryEntryError: UnpackFoodDiaryEntryUseCase,
    entryRepository: FoodDiaryEntryRepository,
    mealRepository: MealRepository,
    dateProvider: DateProvider,
) : ViewModel() {

    val meals =
        mealRepository
            .observeMeals()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = emptyList(),
            )

    val entry =
        entryRepository
            .observe(entryId)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    val possibleMeasurementTypes =
        entry
            .filterNotNull()
            .flatMapLatest { entry -> entry.food.possibleMeasurementTypes }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    val suggestions: StateFlow<List<Measurement>?> =
        entry
            .filterNotNull()
            .flatMapLatest { entry ->
                entry.food.suggestions.map { (listOf(entry.measurement) + it).distinct() }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    val today =
        dateProvider
            .observeDate()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = LocalDate.now(),
            )

    /**
     * The recipe with the ingredient amounts the person changed on this screen, until it is saved.
     * Null while nothing has been changed.
     */
    private val editedFood = MutableStateFlow<DiaryFoodRecipe?>(null)

    /** The food as it will be saved: the entry's own copy, or the edited recipe. */
    val food: StateFlow<DiaryFood?> =
        combine(entry, editedFood) { entry, edited -> edited ?: entry?.food }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    /**
     * Sets the amount of one ingredient, as it is shown for the current portion, in any unit the
     * ingredient supports - grams, a serving (a slice of bread, if that is the product's serving),
     * a package.
     *
     * The other ingredients keep the amounts they are showing, so the dish becomes exactly what is
     * on the plate: the recipe is rebuilt from the scaled ingredients, as a single serving. That is
     * why the whole dish weight changes and is returned - it is the new portion, and the caller
     * moves the amount picker to it so the list and the totals keep agreeing.
     *
     * @return the weight of the new whole dish, or null when there is nothing to change.
     */
    fun editIngredient(
        currentMeasurement: Measurement,
        index: Int,
        measurement: Measurement,
    ): Double? {
        val recipe = food.value as? DiaryFoodRecipe ?: return null

        val shown = recipe.unpack(currentMeasurement)
        val target = shown.getOrNull(index) ?: return null
        val weight = runCatching { target.food.weight(measurement) }.getOrNull() ?: return null
        if (weight <= 0) return null

        val edited =
            recipe.copy(
                servings = 1,
                ingredients =
                    shown.mapIndexed { i, ingredient ->
                        if (i == index) ingredient.copy(measurement = measurement) else ingredient
                    },
            )
        editedFood.value = edited
        return edited.totalWeight
    }

    private val _uiEvents = Channel<UpdateEntryEvent>()
    val uiEvents = _uiEvents.receiveAsFlow()

    fun save(measurement: Measurement, mealId: Long, date: LocalDate) {
        viewModelScope.launch {
            updateFoodDiaryEntryUseCase
                .update(
                    id = entryId,
                    measurement = measurement,
                    mealId = mealId,
                    date = date,
                    food = editedFood.value,
                )
                .onSuccess { _uiEvents.send(UpdateEntryEvent.Saved) }
                .onError {
                    // Explode
                    error("Failed to update diary entry with id $entryId, $it")
                }

            _uiEvents.send(UpdateEntryEvent.Saved)
        }
    }

    fun unpack(measurement: Measurement, mealId: Long, date: LocalDate) {
        viewModelScope.launch {
            // Unpacking reads the entry from the database: changed amounts that were never saved
            // would be lost, and the ingredients would come out with the old grams.
            editedFood.value?.let { edited ->
                updateFoodDiaryEntryUseCase
                    .update(
                        id = entryId,
                        measurement = measurement,
                        mealId = mealId,
                        date = date,
                        food = edited,
                    )
                    .onError { error("Failed to update diary entry with id $entryId, $it") }
            }
            unpackDiaryEntryError
                .unpack(id = entryId, measurement = measurement, mealId = mealId, date = date)
                .onError {
                    // Explode
                    error("Failed to unpack diary entry with id $entryId, $it")
                }

            _uiEvents.send(UpdateEntryEvent.Saved)
        }
    }
}

// These extensions will probably be moved into business when user would be able to choose between
// metric and imperial measurements. This is why they are wrapped in Flow, so they can be
// easily converted to the appropriate measurement system later.

private val DiaryFood.possibleMeasurementTypes: Flow<List<MeasurementType>>
    get() = flowOf(measurementTypes())

private val DiaryFood.suggestions: Flow<List<Measurement>>
    get() = flowOf(measurementSuggestions())

/** The units this food can be measured in. */
internal fun DiaryFood.measurementTypes(): List<MeasurementType> =
    MeasurementType.entries.filter { type ->
        when (type) {
            MeasurementType.Gram -> !isLiquid
            MeasurementType.Ounce -> !isLiquid
            MeasurementType.Milliliter -> isLiquid
            MeasurementType.FluidOunce -> isLiquid
            MeasurementType.Package -> totalWeight != null
            MeasurementType.Serving -> servingWeight != null
        }
    }

/** One default amount per unit, for the picker's chips. */
internal fun DiaryFood.measurementSuggestions(): List<Measurement> =
    measurementTypes().map {
        when (it) {
            MeasurementType.Gram -> Measurement.Gram(Measurement.Gram.DEFAULT)
            MeasurementType.Ounce -> Measurement.Ounce(Measurement.Ounce.DEFAULT)
            MeasurementType.Package -> Measurement.Package(Measurement.Package.DEFAULT)
            MeasurementType.Serving -> Measurement.Serving(Measurement.Serving.DEFAULT)
            MeasurementType.Milliliter -> Measurement.Milliliter(Measurement.Milliliter.DEFAULT)
            MeasurementType.FluidOunce -> Measurement.FluidOunce(Measurement.FluidOunce.DEFAULT)
        }
    }
