package com.gymbuddy.app.audio

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DiagnosticLog(private val nowMs: () -> Long = { System.currentTimeMillis() }) {
    private val lines = mutableListOf<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun add(message: String): String {
        val line = "${fmt.format(Date(nowMs()))}  $message"
        synchronized(lines) { lines += line }
        return line
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }

    fun text(): String = snapshot().joinToString("\n")

    fun clear() {
        synchronized(lines) { lines.clear() }
    }
}

object DiagnosticFiles {
    fun root(filesDir: File): File = File(filesDir, "audio-diagnostic")

    fun newRunDir(filesDir: File, stamp: String): File {
        val dir = File(root(filesDir), "report-$stamp")
        dir.mkdirs()
        return dir
    }

    fun writeReport(
        dir: File,
        header: Map<String, String>,
        log: DiagnosticLog,
    ): File {
        dir.mkdirs()
        val file = File(dir, "report.txt")
        val body = buildString {
            appendLine("GymBuddy audio diagnostic")
            header.forEach { (k, v) -> appendLine("$k: $v") }
            appendLine("adb: ${AudioRoutes.ADB_PULL}")
            appendLine()
            appendLine(log.text())
        }
        file.writeText(body)
        return file
    }
}
