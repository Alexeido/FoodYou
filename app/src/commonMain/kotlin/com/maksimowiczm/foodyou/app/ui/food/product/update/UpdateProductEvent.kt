package com.maksimowiczm.foodyou.app.ui.food.product.update

internal sealed interface UpdateProductEvent {

    data object Updated : UpdateProductEvent

    data object Refreshed : UpdateProductEvent

    data object RefreshNoSource : UpdateProductEvent

    data object RefreshNotFound : UpdateProductEvent

    data object RefreshUnauthorized : UpdateProductEvent

    data object RefreshError : UpdateProductEvent
}
