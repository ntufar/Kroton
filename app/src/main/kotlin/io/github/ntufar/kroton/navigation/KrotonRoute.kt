package io.github.ntufar.kroton.navigation

import kotlinx.serialization.Serializable

sealed interface KrotonRoute {
    @Serializable
    data object Workout : KrotonRoute

    @Serializable
    data object History : KrotonRoute

    @Serializable
    data object Measure : KrotonRoute

    @Serializable
    data object Stats : KrotonRoute

    @Serializable
    data object Exercises : KrotonRoute

    @Serializable
    data object Settings : KrotonRoute
}

data class BottomBarDestination(
    val route: KrotonRoute,
    val labelRes: Int,
)
