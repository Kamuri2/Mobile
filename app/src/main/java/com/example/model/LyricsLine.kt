package com.example.model

data class LyricsLine(
    val timestampMs: Long,
    val text: String
) {
    fun formatTime(): String {
        val totalSecs = timestampMs / 1000
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format("%02d:%02d", mins, secs)
    }
}

object LyricsParser {
    /**
     * Parses LRC format string e.g. "[00:12.50]Line of lyrics text"
     */
    fun parseLrc(lrcText: String): List<LyricsLine> {
        if (lrcText.isBlank()) return emptyList()
        val lines = mutableListOf<LyricsLine>()
        // Permissive regex for formats like [mm:ss], [m:ss.ms], <mm:ss.ms>, [mm:ss:ms]
        val lrcRegex = Regex("(?:\\[|<)(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?(?:\\]|>)(.*)")
        
        var hasAnyTimestamps = false

        lrcText.lines().forEach { line ->
            val cleanLine = line.trim()
            val match = lrcRegex.find(cleanLine)
            if (match != null) {
                hasAnyTimestamps = true
                val minsStr = match.groupValues[1]
                val secsStr = match.groupValues[2]
                val msStr = match.groupValues[3]
                val content = match.groupValues[4]
                
                val mins = minsStr.toLongOrNull() ?: 0L
                val secs = secsStr.toLongOrNull() ?: 0L
                val msFactor = if (msStr.length == 2) 10L else if (msStr.length == 1) 100L else 1L
                val ms = if (msStr.isNotEmpty()) (msStr.toLongOrNull() ?: 0L) * msFactor else 0L
                val timestamp = mins * 60_000L + secs * 1_000L + ms
                
                if (content.isNotBlank()) {
                    lines.add(LyricsLine(timestamp, content.trim()))
                }
            } else if (cleanLine.isNotBlank()) {
                // Keep the text, but assign it a negative timestamp so it doesn't mess up playback
                // if it's metadata. We will filter these out later if it's a synced file.
                val content = if (cleanLine.startsWith("[") || cleanLine.startsWith("<")) {
                    cleanLine.replace(Regex("(?:\\[|<).*?(?:\\]|>)"), "").trim()
                } else {
                    cleanLine
                }
                if (content.isNotBlank()) {
                    lines.add(LyricsLine(-1L, content))
                }
            }
        }
        
        // If there were no valid timestamps at all, it's an unsynced lyrics file.
        // Returning an empty list tells the UI to render the raw string as static text.
        if (!hasAnyTimestamps) {
            return emptyList()
        }

        // For synced lyrics, assign any unsynced interstitial lines to the previous timestamp
        var lastTimestamp = 0L
        val fixedLines = lines.map { 
            if (it.timestampMs >= 0) {
                lastTimestamp = it.timestampMs
                it
            } else {
                it.copy(timestampMs = lastTimestamp)
            }
        }

        return fixedLines.sortedBy { it.timestampMs }
    }
}