package io.github.ntufar.kroton.export

/** CSV counterparts of the two highest-value XLSX sheets (§6.2/§6.1) — `Sets` (the pivot table)
 * and `Workouts`. The other seven XLSX sheets don't have a CSV equivalent yet; this is a scoped
 * simplification, not an oversight (see PROGRESS.md). */
object CsvExportBuilder {
    fun setsCsv(backup: BackupJson): String {
        val exercisesById = backup.exercises.associateBy { it.id }
        val prSetIds = backup.records.mapNotNull { it.workoutSetId }.toSet()
        val headers =
            listOf(
                "Date", "Workout", "Exercise", "Primary Muscle", "Set #", "Set Type", "Weight (kg)",
                "Reps", "RPE", "Volume (kg)", "Est 1RM (kg)", "Is PR",
            )
        val rows =
            backup.workouts.flatMap { workout ->
                workout.exercises.flatMap { we ->
                    val exercise = exercisesById[we.exerciseId]
                    we.sets.mapIndexed { index, set ->
                        val volume = if (set.weightKg != null && set.reps != null) set.weightKg * set.reps else null
                        listOf(
                            workout.localDate.toString(),
                            workout.name,
                            exercise?.name ?: "Unknown",
                            exercise?.primaryMuscle ?: "",
                            (index + 1).toString(),
                            set.setType,
                            set.weightKg?.toString() ?: "",
                            set.reps?.toString() ?: "",
                            set.rpe?.toString() ?: "",
                            volume?.toString() ?: "",
                            set.estimated1RmKg?.toString() ?: "",
                            if (set.id in prSetIds) "Yes" else "No",
                        )
                    }
                }
            }
        return CsvWriter.toCsv(headers, rows)
    }

    fun workoutsCsv(backup: BackupJson): String {
        val headers = listOf("Date", "Name", "Duration (sec)", "Volume (kg)", "Sets", "PRs", "Notes")
        val rows =
            backup.workouts.map { w ->
                listOf(
                    w.localDate.toString(),
                    w.name,
                    w.durationSec.toString(),
                    w.totalVolumeKg.toString(),
                    w.totalSets.toString(),
                    w.prCount.toString(),
                    w.notes ?: "",
                )
            }
        return CsvWriter.toCsv(headers, rows)
    }
}
