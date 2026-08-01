package io.github.ntufar.kroton.domain

data class PlateOption(val weightKg: Double, val count: Int)

data class PlateCalculatorResult(
    val perSideKg: List<Double>,
    val achievedKg: Double,
    val deltaKg: Double,
)

/**
 * Greedy solve for plates per side (spec §4.5): largest plates first, respecting available
 * counts, given the total per-side load actually needed once the bar is subtracted.
 */
object PlateCalculator {
    fun solve(
        targetKg: Double,
        barKg: Double,
        available: List<PlateOption>,
    ): PlateCalculatorResult {
        val perSideTarget = ((targetKg - barKg) / 2).coerceAtLeast(0.0)
        val remainingCounts = available.associate { it.weightKg to it.count }.toMutableMap()
        val sortedPlates = available.map { it.weightKg }.distinct().sortedDescending()

        val perSide = mutableListOf<Double>()
        var remaining = perSideTarget
        for (plateKg in sortedPlates) {
            var count = remainingCounts[plateKg] ?: 0
            while (count > 0 && plateKg <= remaining + PLATE_EPSILON_KG) {
                perSide += plateKg
                remaining -= plateKg
                count--
            }
            remainingCounts[plateKg] = count
        }

        val achievedPerSide = perSide.sum()
        val achievedKg = barKg + achievedPerSide * 2
        return PlateCalculatorResult(
            perSideKg = perSide,
            achievedKg = achievedKg,
            deltaKg = targetKg - achievedKg,
        )
    }

    private const val PLATE_EPSILON_KG = 0.001
}
