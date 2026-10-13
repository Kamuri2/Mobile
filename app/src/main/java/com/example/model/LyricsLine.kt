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
    // Matches enclosed timestamps like [00:12.50], [00:12.500], [00:12:50], <00:12.50>, [0:12.50]
    private val timestampRegex = Regex("""(?:\[|<)\s*(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\s*(?:\]|>)""")
    // Matches enhanced syllable timestamps like <00:12.50>
    private val syllableRegex = Regex("""<\s*\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?\s*>""")
    // Matches ANY bracketed metadata header tag e.g. [ar:Artist], [artist:Artist], [ti:Title], [length:03:45], [offset:0], [written by:...], etc.
    private val metadataTagRegex = Regex("""\[\s*[a-zA-Z_ ]{1,24}\s*:[^\]]*\]""", RegexOption.IGNORE_CASE)
    // Matches raw metadata key-value lines without brackets or with prefix
    private val metadataLineRegex = Regex("""(?i)^\s*(?:ti|ar|al|by|offset|re|ve|length|id|au|hash|creator|program|version|kana|romaji|translation|artist|album|title|composer|lyricist|tool|encoding|source|written\s*by|synced\s*by|sync\s*by|lyrics\s*by|created\s*by|made\s*by|encoded\s*by|editor|url|qq|site|karaoke)\s*:.*""")
    // Matches ripper, encoder, or advertising residue lines
    private val residueLineRegex = Regex("""(?i)^\s*(?:Exact\s*Audio\s*Copy|LAME|Lavf|Encoded\s*by|Ripped\s*by|iTunNORM|iTunSMPB|Frame:\s*[A-Z0-9]{3,4}|[0-9a-f]{8}(?:\s+[0-9a-f]{8}){3,}|synced\s*by|lyrics\s*(?:provided\s*)?by|created\s*by|created\s*with|editor\s*:|qq\s*music|musixmatch|minilyrics|lrc\s*maker|lrc\s*generator|www\.[a-z0-9.-]+\.[a-z]{2,}|https?://|visit\s*our|all\s*rights\s*reserved|copyright\s*\(c\)|downloaded\s*from|translated\s*by|end\s*of\s*lyrics).*""")
    private val offsetRegex = Regex("""\[offset:\s*([+-]?\d+)\s*\]""", RegexOption.IGNORE_CASE)

    fun parseLrc(lrcText: String): List<LyricsLine> {
        if (lrcText.isBlank()) return emptyList()
        val parsedLines = mutableListOf<LyricsLine>()

        var globalOffset = 0L
        val offsetMatch = offsetRegex.find(lrcText)
        if (offsetMatch != null) {
            globalOffset = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
        }

        var hasAnyTimestamps = false

        lrcText.lines().forEach { rawLine ->
            val cleanLine = rawLine.trim()
            if (cleanLine.isBlank()) return@forEach

            // Skip lines that are purely metadata headers or ripper residues
            if (cleanLine.matches(Regex("""^\[\s*[a-zA-Z_ ]{1,24}\s*:[^\]]*\]$""")) ||
                metadataLineRegex.matches(cleanLine) ||
                residueLineRegex.matches(cleanLine)) {
                return@forEach
            }

            // Find all timestamps on this line (supports multiple timestamps e.g. [00:12.50][01:15.20]Chorus)
            val timeMatches = timestampRegex.findAll(cleanLine).toList()

            if (timeMatches.isNotEmpty()) {
                hasAnyTimestamps = true

                // Strip all timestamp tags, enhanced syllable tags, and metadata tags to extract pure lyric text
                var cleanText = cleanLine.replace(timestampRegex, "")
                cleanText = cleanText.replace(syllableRegex, "")
                cleanText = cleanText.replace(metadataTagRegex, "").trim()

                // If after removing timestamps and metadata tags, the line is blank or is metadata residue, skip
                if (cleanText.isBlank() || metadataLineRegex.matches(cleanText) || residueLineRegex.matches(cleanText)) {
                    return@forEach
                }

                // Skip if this single line is a known corrupted token or advertising URL
                if (cleanText.matches(Regex("""(?i)^https?://.*""")) || cleanText.matches(Regex("""(?i)^www\..*"""))) {
                    return@forEach
                }

                for (match in timeMatches) {
                    val mins = match.groupValues[1].toLongOrNull() ?: 0L
                    val secs = match.groupValues[2].toLongOrNull() ?: 0L
                    val fractionStr = match.groupValues.getOrNull(3) ?: ""
                    val millis = when (fractionStr.length) {
                        0 -> 0L
                        1 -> (fractionStr.toLongOrNull() ?: 0L) * 100L
                        2 -> (fractionStr.toLongOrNull() ?: 0L) * 10L
                        3 -> fractionStr.toLongOrNull() ?: 0L
                        else -> fractionStr.substring(0, 3).toLongOrNull() ?: 0L
                    }
                    val totalMs = (mins * 60_000L) + (secs * 1_000L) + millis + globalOffset
                    val finalTimeMs = totalMs.coerceAtLeast(0L)

                    parsedLines.add(LyricsLine(finalTimeMs, cleanText))
                }
            } else {
                // If it's a section tag like [Chorus], [Verse 1] or raw text
                var textWithoutTags = cleanLine.replace(metadataTagRegex, "").trim()
                textWithoutTags = textWithoutTags.replace(Regex("""^\[\s*(.*?)\s*\]$"""), "$1").trim()
                if (textWithoutTags.isNotBlank() &&
                    !metadataLineRegex.matches(textWithoutTags) &&
                    !residueLineRegex.matches(textWithoutTags) &&
                    !com.example.data.AudioLyricsExtractor.isCorruptedLyrics(textWithoutTags)) {
                    parsedLines.add(LyricsLine(-1L, textWithoutTags))
                }
            }
        }

        // If no timestamps were found at all, return emptyList so caller treats it as unsynced
        if (!hasAnyTimestamps) {
            return emptyList()
        }

        // For synced lyrics, resolve any non-timestamped lines to the previous timestamp
        var currentMs = 0L
        val sortedLines = parsedLines
            .map { line ->
                if (line.timestampMs >= 0L) {
                    currentMs = line.timestampMs
                    line
                } else {
                    line.copy(timestampMs = currentMs)
                }
            }
            .filter { it.text.isNotBlank() }
            .sortedBy { it.timestampMs }

        return sortedLines
    }
}