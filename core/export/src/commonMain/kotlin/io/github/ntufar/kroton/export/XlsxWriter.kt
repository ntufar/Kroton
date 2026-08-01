package io.github.ntufar.kroton.export

import org.dhatim.fastexcel.Workbook
import org.dhatim.fastexcel.Worksheet
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

private const val KG_TO_LB = 2.20462
private const val APP_VERSION_FORMAT = "1.0"

/**
 * Writes the §6.1 XLSX report — "the reason this app exists". Every sheet gets a frozen header
 * row, an autofilter, and real date/numeric cell types rather than strings, since that's what
 * makes the file pivot-ready in a spreadsheet app.
 */
object XlsxWriter {
    fun write(
        backup: BackupJson,
        outputStream: OutputStream,
    ) {
        Workbook(outputStream, "Kroton", APP_VERSION_FORMAT).use { wb ->
            writeSetsSheet(wb, backup)
            writeWorkoutsSheet(wb, backup)
            writeExercisesSheet(wb, backup)
            writeMeasurementsSheet(wb, backup)
            writeMeasurementsWideSheet(wb, backup)
            writeRecordsSheet(wb, backup)
            writeRoutinesSheet(wb, backup)
            writeSummarySheet(wb, backup)
            writeMetaSheet(wb, backup)
        }
    }

    private fun finishSheet(
        sheet: Worksheet,
        headers: List<String>,
        rowCount: Int,
    ) {
        sheet.range(0, 0, 0, headers.size - 1).style().bold().set()
        sheet.freezePane(0, 1)
        if (rowCount > 0) sheet.setAutoFilter(0, 0, headers.size - 1)
        headers.forEachIndexed { col, header -> sheet.value(0, col, header) }
    }

    private fun writeSetsSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers =
            listOf(
                "Date", "ISO Week", "Workout", "Exercise", "Primary Muscle", "Equipment", "Set #",
                "Set Type", "Weight (kg)", "Weight (lb)", "Reps", "RPE", "Volume (kg)", "Est 1RM (kg)", "Is PR",
            )
        val exercisesById = backup.exercises.associateBy { it.id }
        val prSetIds = backup.records.mapNotNull { it.workoutSetId }.toSet()
        val sheet = wb.newWorksheet("Sets")
        var row = 1
        backup.workouts.forEach { workout ->
            val date = parseLocalDate(workout.localDate)
            workout.exercises.forEach { we ->
                val exercise = exercisesById[we.exerciseId]
                we.sets.forEachIndexed { index, set ->
                    var col = 0
                    sheet.value(row, col++, date)
                    sheet.value(row, col++, isoWeekLabel(date))
                    sheet.value(row, col++, workout.name)
                    sheet.value(row, col++, exercise?.name ?: "Unknown")
                    sheet.value(row, col++, exercise?.primaryMuscle ?: "")
                    sheet.value(row, col++, exercise?.equipment ?: "")
                    sheet.value(row, col++, (index + 1).toDouble())
                    sheet.value(row, col++, set.setType)
                    set.weightKg?.let { sheet.value(row, col, it) }
                    col++
                    set.weightKg?.let { sheet.value(row, col, it * KG_TO_LB) }
                    col++
                    set.reps?.let { sheet.value(row, col, it.toDouble()) }
                    col++
                    set.rpe?.let { sheet.value(row, col, it) }
                    col++
                    val volume = if (set.weightKg != null && set.reps != null) set.weightKg * set.reps else null
                    volume?.let { sheet.value(row, col, it) }
                    col++
                    set.estimated1RmKg?.let { sheet.value(row, col, it) }
                    col++
                    sheet.value(row, col, if (set.id in prSetIds) "Yes" else "No")
                    row++
                }
            }
        }
        finishSheet(sheet, headers, row - 1)
    }

    private fun writeWorkoutsSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers = listOf("Date", "Name", "Duration (min)", "Volume (kg)", "Sets", "PRs", "Notes")
        val sheet = wb.newWorksheet("Workouts")
        backup.workouts.forEachIndexed { index, workout ->
            val row = index + 1
            sheet.value(row, 0, parseLocalDate(workout.localDate))
            sheet.value(row, 1, workout.name)
            sheet.value(row, 2, workout.durationSec / SEC_PER_MIN.toDouble())
            sheet.value(row, 3, workout.totalVolumeKg)
            sheet.value(row, 4, workout.totalSets.toDouble())
            sheet.value(row, 5, workout.prCount.toDouble())
            sheet.value(row, 6, workout.notes ?: "")
        }
        finishSheet(sheet, headers, backup.workouts.size)
    }

    private fun writeExercisesSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers =
            listOf(
                "Name",
                "Primary Muscle",
                "Equipment",
                "Custom",
                "Total Sets",
                "Total Volume (kg)",
                "Best Est 1RM (kg)",
            )
        val setsByExercise = backup.workouts.flatMap { it.exercises }.groupBy { it.exerciseId }
        val sheet = wb.newWorksheet("Exercises")
        backup.exercises.forEachIndexed { index, exercise ->
            val row = index + 1
            val allSets = setsByExercise[exercise.id].orEmpty().flatMap { it.sets }
            val totalVolume =
                allSets.sumOf { s -> if (s.weightKg != null && s.reps != null) s.weightKg * s.reps else 0.0 }
            val best1Rm = allSets.mapNotNull { it.estimated1RmKg }.maxOrNull()
            sheet.value(row, 0, exercise.name)
            sheet.value(row, 1, exercise.primaryMuscle)
            sheet.value(row, 2, exercise.equipment)
            sheet.value(row, 3, if (exercise.isCustom) "Yes" else "No")
            sheet.value(row, 4, allSets.size.toDouble())
            sheet.value(row, 5, totalVolume)
            best1Rm?.let { sheet.value(row, 6, it) }
        }
        finishSheet(sheet, headers, backup.exercises.size)
    }

    private fun writeMeasurementsSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers = listOf("Date", "Type", "Value", "Unit")
        val typesById = backup.measurementTypes.associateBy { it.id }
        val sheet = wb.newWorksheet("Measurements")
        backup.measurements.forEachIndexed { index, entry ->
            val row = index + 1
            val type = typesById[entry.typeId]
            sheet.value(row, 0, parseLocalDate(entry.localDate))
            sheet.value(row, 1, type?.displayName ?: "Unknown")
            sheet.value(row, 2, entry.value)
            sheet.value(row, 3, type?.unitKind ?: "")
        }
        finishSheet(sheet, headers, backup.measurements.size)
    }

    private fun writeMeasurementsWideSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val types = backup.measurementTypes.sortedBy { it.sortOrder }
        val headers = listOf("Date") + types.map { it.displayName }
        val byDate = backup.measurements.groupBy { it.localDate }
        val sheet = wb.newWorksheet("Measurements_Wide")
        byDate.keys.sorted().forEachIndexed { index, localDate ->
            val row = index + 1
            sheet.value(row, 0, parseLocalDate(localDate))
            val entriesForDate = byDate[localDate].orEmpty().associateBy { it.typeId }
            types.forEachIndexed { colIndex, type ->
                entriesForDate[type.id]?.let { sheet.value(row, colIndex + 1, it.value) }
            }
        }
        finishSheet(sheet, headers, byDate.size)
    }

    private fun writeRecordsSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers = listOf("Date", "Exercise", "Record Type", "Value")
        val exercisesById = backup.exercises.associateBy { it.id }
        val sheet = wb.newWorksheet("Records")
        backup.records.sortedByDescending { it.achievedAt }.forEachIndexed { index, record ->
            val row = index + 1
            sheet.value(row, 0, java.time.Instant.ofEpochMilli(record.achievedAt))
            sheet.value(row, 1, exercisesById[record.exerciseId]?.name ?: "Unknown")
            sheet.value(row, 2, record.recordType)
            sheet.value(row, 3, record.value)
        }
        finishSheet(sheet, headers, backup.records.size)
    }

    private fun writeRoutinesSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers = listOf("Routine", "Exercise", "Sets", "Notes")
        val exercisesById = backup.exercises.associateBy { it.id }
        val sheet = wb.newWorksheet("Routines")
        var row = 1
        backup.routines.forEach { routine ->
            routine.exercises.forEach { re ->
                sheet.value(row, 0, routine.name)
                sheet.value(row, 1, exercisesById[re.exerciseId]?.name ?: "Unknown")
                sheet.value(row, 2, re.sets.size.toDouble())
                sheet.value(row, 3, re.notes ?: "")
                row++
            }
        }
        finishSheet(sheet, headers, row - 1)
    }

    private fun writeSummarySheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val headers = listOf("Week", "Workouts", "Volume (kg)", "Sets")
        val byWeek = backup.workouts.groupBy { isoWeekLabel(parseLocalDate(it.localDate)) }
        val sheet = wb.newWorksheet("Summary")
        byWeek.keys.sorted().forEachIndexed { index, week ->
            val row = index + 1
            val workouts = byWeek[week].orEmpty()
            sheet.value(row, 0, week)
            sheet.value(row, 1, workouts.size.toDouble())
            sheet.value(row, 2, workouts.sumOf { it.totalVolumeKg })
            sheet.value(row, 3, workouts.sumOf { it.totalSets }.toDouble())
        }
        finishSheet(sheet, headers, byWeek.size)
    }

    private fun writeMetaSheet(
        wb: Workbook,
        backup: BackupJson,
    ) {
        val sheet = wb.newWorksheet("Meta")
        sheet.value(0, 0, "App version")
        sheet.value(0, 1, backup.appVersion)
        sheet.value(1, 0, "Schema version")
        sheet.value(1, 1, backup.schemaVersion.toDouble())
        sheet.value(2, 0, "Exported at")
        sheet.value(2, 1, backup.exportedAt)
        sheet.value(3, 0, "Weight unit")
        sheet.value(3, 1, backup.profile.weightUnit)
        sheet.value(4, 0, "Length unit")
        sheet.value(4, 1, backup.profile.lengthUnit)
    }

    private fun parseLocalDate(localDate: Int): LocalDate =
        LocalDate.parse(localDate.toString(), DateTimeFormatter.ofPattern("yyyyMMdd"))

    private fun isoWeekLabel(date: LocalDate): String {
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return "$year-W${week.toString().padStart(2, '0')}"
    }

    private const val SEC_PER_MIN = 60
}
