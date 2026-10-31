package hu.angolmagyar.fordito.intelligence

/** UTF-8 CSV interchange: english,hungarian,example. Quotes and commas are supported. */
data class DictionaryEntry(val english: String, val hungarian: String, val example: String = "")

object DictionaryCsv {
    fun export(entries: List<DictionaryEntry>): String = buildString {
        appendLine("english,hungarian,example")
        entries.forEach { e -> appendLine(listOf(e.english, e.hungarian, e.example).joinToString(",") { quote(it) }) }
    }
    fun parse(csv: String): List<DictionaryEntry> {
        val rows = parseRows(csv).filter { it.any(String::isNotBlank) }.mapIndexed { index, row ->
            if (index == 0 && row.isNotEmpty()) row.toMutableList().also { it[0] = it[0].removePrefix("\uFEFF") } else row
        }
        return rows.drop(if (rows.firstOrNull()?.firstOrNull()?.trim()?.equals("english", true) == true) 1 else 0)
            .mapNotNull { r -> if (r.size < 2 || r[0].isBlank() || r[1].isBlank()) null else DictionaryEntry(r[0].trim(), r[1].trim(), r.getOrElse(2) { "" }.trim()) }
    }
    private fun quote(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    private fun parseRows(input: String): List<List<String>> {
        val rows = mutableListOf<List<String>>(); var row = mutableListOf<String>(); val cell = StringBuilder(); var quoted = false; var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '"' && quoted && i + 1 < input.length && input[i+1] == '"' -> { cell.append('"'); i++ }
                c == '"' -> quoted = !quoted
                (c == ',' || c == '\n' || c == '\r') && !quoted -> {
                    row.add(cell.toString()); cell.setLength(0)
                    if (c == ',' ) { /* next column */ } else { if (row.isNotEmpty()) rows.add(row); row = mutableListOf(); if (c == '\r' && i+1 < input.length && input[i+1] == '\n') i++ }
                }
                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) { row.add(cell.toString()); rows.add(row) }
        return rows
    }
}
