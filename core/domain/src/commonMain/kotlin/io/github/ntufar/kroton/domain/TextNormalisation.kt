package io.github.ntufar.kroton.domain

/** Search-key normalisation used by [ExerciseSeeder] and exercise search (spec §5.5). */
fun normaliseExerciseName(name: String): String = name.trim().lowercase()
