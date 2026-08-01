package io.github.ntufar.kroton.export

/** Per-entity UTF-8-with-BOM, RFC 4180-quoted CSV (spec §6.2) — same denormalised schemas as the
 * XLSX sheets, offered individually or as a ZIP bundle by the caller. */
object CsvWriter {
    const val UTF8_BOM = "﻿"

    fun toCsv(
        headers: List<String>,
        rows: List<List<String>>,
    ): String {
        val builder = StringBuilder(UTF8_BOM)
        builder.append(headers.joinToString(",") { quote(it) }).append("\r\n")
        rows.forEach { row -> builder.append(row.joinToString(",") { quote(it) }).append("\r\n") }
        return builder.toString()
    }

    private fun quote(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuoting) "\"${value.replace("\"", "\"\"")}\"" else value
    }
}
