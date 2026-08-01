package io.github.ntufar.kroton.datastore

import io.github.ntufar.kroton.model.OneRmFormula

data class UserPreferences(
    val weightUnit: String = "KG",
    val theme: String = "DARK",
    val dynamicColour: Boolean = true,
    val defaultRestSec: Int = 90,
    val oneRmFormula: OneRmFormula = OneRmFormula.EPLEY,
    val firstDayOfWeek: Int = 1,
)
