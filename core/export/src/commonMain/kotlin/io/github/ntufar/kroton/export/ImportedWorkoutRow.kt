package io.github.ntufar.kroton.export

/** One imported set, common shape for both Hevy and Strong CSV rows (spec §6.6). Date/time
 * fields stay raw strings here — parsing them to epoch millis needs `java.time` formatter
 * fallbacks and belongs with the DB-writing step (`core/domain`'s import repository), not this
 * pure-parsing module. */
data class ImportedWorkoutRow(
    val workoutTitle: String,
    val rawStartTime: String,
    val rawEndTime: String?,
    val exerciseName: String,
    val setIndex: Int,
    val setType: String?,
    val weightKg: Double?,
    val reps: Int?,
    val distanceM: Double?,
    val durationSec: Int?,
    val rpe: Double?,
    val notes: String?,
)

/**
 * Hevy's export is one row per set (spec §6.6): `title, start_time, end_time, description,
 * exercise_title, superset_id, exercise_notes, set_index, set_type, weight_kg, reps,
 * distance_km, duration_seconds, rpe`. Mapped by header name, not position, per the spec's own
 * caution — **not verified against a real Hevy export** in this pass (no sample was available);
 * revisit the exact header names/formats against a real file before shipping import.
 */
object HevyImport {
    fun parse(rows: List<Map<String, String>>): List<ImportedWorkoutRow> =
        rows.mapNotNull { row ->
            val title = row["title"] ?: return@mapNotNull null
            val startTime = row["start_time"] ?: return@mapNotNull null
            val exerciseTitle = row["exercise_title"] ?: return@mapNotNull null
            ImportedWorkoutRow(
                workoutTitle = title,
                rawStartTime = startTime,
                rawEndTime = row["end_time"],
                exerciseName = exerciseTitle,
                setIndex = row["set_index"]?.toIntOrNull() ?: 0,
                setType = row["set_type"],
                weightKg = row["weight_kg"]?.toDoubleOrNull(),
                reps = row["reps"]?.toIntOrNull(),
                distanceM = row["distance_km"]?.toDoubleOrNull()?.let { it * METERS_PER_KM },
                durationSec = row["duration_seconds"]?.toIntOrNull(),
                rpe = row["rpe"]?.toDoubleOrNull(),
                notes = row["exercise_notes"],
            )
        }

    private const val METERS_PER_KM = 1000.0
}

/**
 * Strong's CSV is similarly one row per set but with different headers — commonly `Date,
 * Workout Name, Duration, Exercise Name, Set Order, Weight, Reps, Distance, Seconds, Notes,
 * Workout Notes, RPE` (weight/distance in the user's configured unit). **Not verified against a
 * real Strong export** in this pass, same caveat as `HevyImport`.
 */
object StrongImport {
    fun parse(rows: List<Map<String, String>>): List<ImportedWorkoutRow> =
        rows.mapNotNull { row ->
            val date = row["Date"] ?: return@mapNotNull null
            val exerciseName = row["Exercise Name"] ?: return@mapNotNull null
            ImportedWorkoutRow(
                workoutTitle = row["Workout Name"] ?: "Workout",
                rawStartTime = date,
                rawEndTime = null,
                exerciseName = exerciseName,
                setIndex = row["Set Order"]?.toIntOrNull() ?: 0,
                setType = null,
                weightKg = row["Weight"]?.toDoubleOrNull(),
                reps = row["Reps"]?.toIntOrNull(),
                distanceM = row["Distance"]?.toDoubleOrNull(),
                durationSec = row["Seconds"]?.toIntOrNull(),
                rpe = row["RPE"]?.toDoubleOrNull(),
                notes = row["Notes"],
            )
        }
}
