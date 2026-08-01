package io.github.ntufar.kroton.domain

object VolumeCalculator {
    fun setVolume(
        weightKg: Double,
        reps: Int,
    ): Double = weightKg * reps

    fun sessionVolume(setVolumes: List<Double>): Double = setVolumes.sum()

    fun muscleGroupVolume(
        primaryVolume: Double,
        secondaryVolumes: List<Double>,
        secondaryMuscleCredit: Double = 0.5,
    ): Double = primaryVolume + secondaryVolumes.sumOf { it * secondaryMuscleCredit }
}
