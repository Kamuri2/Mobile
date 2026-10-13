package com.example.data

data class LrcLine(
    val timeMs: Long,
    val text: String
)

object LrcParser {
    // Matches [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx], [m:ss.xx], [mm:ss], [mm:ss:xxx] with optional spacing
    private val timeTagRegex = Regex("""(?:\[|<)\s*(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?\s*(?:\]|>)""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)
    private val enhancedLrcRegex = Regex("""<\s*\d{1,3}:\d{2}(?:[.:]\d{1,3})?\s*>""")
    private val metadataRegex = Regex("""^\[\s*(?:ti|ar|al|by|offset|re|ve|length|id|au|hash|creator|program|version|kana|romaji|translation)\s*:[^\]]*\]""", RegexOption.IGNORE_CASE)

    fun parse(lrcContent: String): List<LrcLine> {
        if (lrcContent.isBlank()) return emptyList()
        val lines = mutableListOf<LrcLine>()
        var globalOffset = 0L

        // Find offset first if it exists
        val offsetMatch = offsetRegex.find(lrcContent)
        if (offsetMatch != null) {
            globalOffset = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
        }

        lrcContent.lines().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isBlank()) return@forEach

            // Ignore standard metadata tags
            if (metadataRegex.containsMatchIn(line)) return@forEach

            val matches = timeTagRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                // Strip all timestamp tags and enhanced tags to get the pure lyric text
                var text = line.replace(timeTagRegex, "")
                text = text.replace(enhancedLrcRegex, "").trim()

                // Allow empty text lines, as instrumental sections use them to display a blank break
                for (match in matches) {
                    val mins = match.groupValues[1].toLongOrNull() ?: 0L
                    val secs = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3) ?: ""
                    val millis = when (fractionStr.length) {
                        0 -> 0L
                        1 -> (fractionStr.toLongOrNull() ?: 0L) * 100
                        2 -> (fractionStr.toLongOrNull() ?: 0L) * 10
                        3 -> fractionStr.toLongOrNull() ?: 0L
                        else -> fractionStr.substring(0, 3).toLongOrNull() ?: 0L
                    }
                    val totalMs = (mins * 60 * 1000) + (secs * 1000) + millis + globalOffset

                    // Avoid negative times due to negative offset
                    val finalTimeMs = if (totalMs < 0) 0L else totalMs
                    lines.add(LrcLine(finalTimeMs, text))
                }
            }
        }

        return lines.sortedBy { it.timeMs }
    }

    fun getCurrentLineIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        return lines.indexOfLast { it.timeMs <= positionMs }
    }
}
