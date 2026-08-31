package com.gymbuddy.persist

/**
 * Single source of truth for the on-disk schema.
 * Loaded from [persist/schemas/v1.sql], shipped on the :persist classpath.
 */
object SchemaSql {
    const val VERSION = 1
    const val RESOURCE = "/v1.sql"

    fun ddl(): String {
        val stream = SchemaSql::class.java.getResourceAsStream(RESOURCE)
            ?: error("missing classpath resource $RESOURCE (persist/schemas/v1.sql)")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    fun statements(): List<String> = splitSql(ddl())

    fun apply(exec: (String) -> Unit) {
        statements().forEach(exec)
    }

    internal fun splitSql(sql: String): List<String> {
        val stripped = sql.lineSequence().joinToString("\n") { line ->
            val comment = line.indexOf("--")
            if (comment >= 0) line.substring(0, comment) else line
        }
        return stripped.split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }
}
