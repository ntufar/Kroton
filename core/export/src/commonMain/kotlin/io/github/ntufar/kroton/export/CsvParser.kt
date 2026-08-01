package io.github.ntufar.kroton.export

/** RFC 4180-ish CSV parsing (quoted fields, embedded commas/newlines, `""` escaping) — the
 * counterpart to `CsvWriter`, used by Hevy/Strong import (spec §6.6) to read third-party exports
 * by header name rather than column position. */
object CsvParser {
    fun parseRows(text: String): List<Map<String, String>> {
        val rows = parseRecords(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first()
        return rows.drop(1).map {
                row ->
            header.mapIndexed { index, name -> name.trim() to row.getOrElse(index) { "" } }.toMap()
        }
    }

    private fun parseRecords(text: String): List<List<String>> {
        val cleaned = text.removePrefix(CsvWriter.UTF8_BOM).removePrefix("")
        val records = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < cleaned.length) {
            val c = cleaned[i]
            when {
                inQuotes && c == '"' && i + 1 < cleaned.length && cleaned[i + 1] == '"' -> {
                    field.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> {
                    currentRow.add(field.toString())
                    field.clear()
                }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < cleaned.length && cleaned[i + 1] == '\n') i++
                    currentRow.add(field.toString())
                    field.clear()
                    if (currentRow.any { it.isNotEmpty() } || currentRow.size > 1) records.add(currentRow.toList())
                    currentRow.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(field.toString())
            records.add(currentRow.toList())
        }
        return records
    }
}
