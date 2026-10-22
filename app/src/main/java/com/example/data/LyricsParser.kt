package com.example.data

import com.example.model.LyricLine
import java.util.regex.Pattern

object LyricsParser {
    // Regex matching [mm:ss.xx] or [mm:ss:xx] or [mm:ss]
    private val TIME_TAG_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")

    data class ParseResult(
        val lines: List<LyricLine>,
        val isSynced: Boolean
    )

    fun parse(rawText: String?, durationMs: Long = 0L): ParseResult {
        if (rawText.isNullOrBlank() || isCorruptedLyrics(rawText)) {
            return ParseResult(emptyList(), false)
        }

        val cleaned = rawText.replace("\r\n", "\n").replace("\r", "\n")
        val rawLines = cleaned.lines().map { it.trim() }.filter { it.isNotBlank() }

        val syncedLines = mutableListOf<LyricLine>()

        for (line in rawLines) {
            val matcher = TIME_TAG_PATTERN.matcher(line)
            var lastMatchEnd = 0
            val timestamps = mutableListOf<Long>()

            while (matcher.find()) {
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3)
                val millis = when {
                    fractionStr == null -> 0L
                    fractionStr.length == 1 -> fractionStr.toLong() * 100
                    fractionStr.length == 2 -> fractionStr.toLong() * 10
                    else -> fractionStr.take(3).toLong()
                }
                val totalMs = (minutes * 60 + seconds) * 1000 + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val textAfterTags = line.substring(lastMatchEnd).trim()
                for (time in timestamps) {
                    syncedLines.add(LyricLine(timestampMs = time, text = textAfterTags))
                }
            }
        }

        if (syncedLines.isNotEmpty()) {
            val sorted = syncedLines.sortedBy { it.timestampMs }
            return ParseResult(sorted, true)
        }

        // If not synced, filter out standard LRC metadata tags like [ar:...] [ti:...] [al:...]
        val plainLines = rawLines
            .filterNot { it.startsWith("[") && it.endsWith("]") && it.contains(":") }
            .mapIndexed { index, text ->
                val estimatedTime = if (rawLines.isNotEmpty() && durationMs > 0) {
                    (index.toLong() * durationMs) / rawLines.size
                } else {
                    index.toLong() * 4000L
                }
                LyricLine(timestampMs = estimatedTime, text = text)
            }

        return ParseResult(plainLines, false)
    }

    fun isCorruptedLyrics(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 4) return true
        // Check for binary or HTML garbage
        if (trimmed.startsWith("<!DOCTYPE", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.contains("<head>", ignoreCase = true) ||
            trimmed.contains("404 Not Found", ignoreCase = true) ||
            trimmed.contains("Access Denied", ignoreCase = true)) {
            return true
        }

        // Check for high percentage of control or non-printable characters
        var nonPrintableCount = 0
        for (char in trimmed) {
            if (char.code in 0..8 || char.code in 14..31) {
                nonPrintableCount++
            }
        }
        return nonPrintableCount > 5
    }

    fun getActiveLineIndex(lines: List<LyricLine>, currentPositionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var activeIndex = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= currentPositionMs) {
                activeIndex = i
            } else {
                break
            }
        }
        return activeIndex
    }
}
