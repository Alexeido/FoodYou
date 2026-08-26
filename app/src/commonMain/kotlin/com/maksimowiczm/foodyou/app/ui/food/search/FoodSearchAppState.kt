package com.maksimowiczm.foodyou.app.ui.food.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable

@Composable
fun rememberFoodSearchAppState(
    searchTextFieldState: TextFieldState = rememberTextFieldState(),
    showBarcodeScanner: Boolean = false,
): FoodSearchAppState {
    val showBarcodeScanner =
        rememberSaveable(showBarcodeScanner) { mutableStateOf(showBarcodeScanner) }

    val focused = remember { mutableStateOf(false) }

    val listStates = rememberListStates()

    return remember(searchTextFieldState, showBarcodeScanner, focused, listStates) {
        FoodSearchAppState(
            searchTextFieldState = searchTextFieldState,
            showBarcodeScannerState = showBarcodeScanner,
            focusedState = focused,
            listStates = listStates,
        )
    }
}

@Stable
class FoodSearchAppState(
    val searchTextFieldState: TextFieldState,
    showBarcodeScannerState: MutableState<Boolean>,
    focusedState: MutableState<Boolean>,
    val listStates: ListStates,
) {
    var showBarcodeScanner by showBarcodeScannerState

    /** Whether the search field currently has focus (drives the inline suggestions panel). */
    var focused by focusedState
}

class ListStates(
    val recent: LazyListState,
    val yourFood: LazyListState,
    val openFoodFacts: LazyListState,
    val usda: LazyListState,
    val swiss: LazyListState,
    val custom: LazyListState,
)

@Composable
private fun rememberListStates(): ListStates {
    val recent = rememberLazyListState()
    val yourFood = rememberLazyListState()
    val openFoodFacts = rememberLazyListState()
    val usda = rememberLazyListState()
    val swiss = rememberLazyListState()
    val custom = rememberLazyListState()

    return remember(recent, yourFood, openFoodFacts, usda, swiss, custom) {
        ListStates(
            recent = recent,
            yourFood = yourFood,
            openFoodFacts = openFoodFacts,
            usda = usda,
            swiss = swiss,
            custom = custom,
        )
    }
}
