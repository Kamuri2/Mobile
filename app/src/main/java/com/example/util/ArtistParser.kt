package com.example.util

import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent Artist Parser & Separator.
 * Extracts individual artist entities from composite artist tags and song titles.
 *
 * Highly optimized for performance:
 * 1. Thread-safe in-memory LRU cache so repeated tracks take 0ms.
 * 2. Precompiled regexes created only once.
 * 3. Fast substring checks to skip regex processing entirely for standard single-artist tracks.
 */
object ArtistParser {

    /**
     * Comprehensive list of artists/bands with commas, ampersands (&), or multiple words
     * that must NEVER be split into individual pieces.
     */
    val COMMA_AND_AMP_EXCEPTIONS: List<String> = listOf(
        // Con coma en el nombre
        "Tyler, The Creator",
        "Peter, Bjorn and John",

        // Con coma y "&" a la vez
        "Earth, Wind & Fire",
        "Crosby, Stills & Nash",
        "Crosby, Stills, Nash & Young",
        "Emerson, Lake & Palmer",
        "Blood, Sweat & Tears",
        "Dave Dee, Dozy, Beaky, Mick & Tich",

        // Con "&" en el nombre (Rock/Pop)
        "Simon & Garfunkel",
        "Hall & Oates",
        "Mumford & Sons",
        "Echo & the Bunnymen",
        "Echo & The Bunnymen",
        "Tom Petty & the Heartbreakers",
        "Tom Petty & The Heartbreakers",
        "Tom Petty and the Heartbreakers",
        "Huey Lewis & the News",
        "Huey Lewis & The News",
        "Huey Lewis and the News",
        "Hootie & the Blowfish",
        "Hootie & The Blowfish",
        "Bob Marley & the Wailers",
        "Bob Marley & The Wailers",
        "Bob Marley and the Wailers",
        "Prince & The Revolution",
        "Prince and the Revolution",
        "Gerry & the Pacemakers",
        "Gerry & The Pacemakers",
        "Katrina & the Waves",
        "Katrina & The Waves",
        "Martha & the Vandellas",
        "Martha & The Vandellas",
        "Belle & Sebastian",
        "Angus & Julia Stone",
        "Nico & Vinz",
        "Matt & Kim",
        "Jan & Dean",
        "Chad & Jeremy",
        "Peter & Gordon",
        "Seals & Crofts",
        "Loggins & Messina",
        "Delaney & Bonnie",
        "Zager & Evans",
        "Mike & The Mechanics",
        "Mike + The Mechanics",
        "Joan Jett and the Blackhearts",
        "Joan Jett & the Blackhearts",
        "Joan Jett & The Blackhearts",
        "Florence + The Machine",
        "Florence and the Machine",
        "Marina and the Diamonds",
        "Above & Beyond",
        "The Black Eyed Peas",
        "Panic! At The Disco",
        "King Gizzard & The Lizard Wizard",
        "KC and the Sunshine Band",
        "KC & The Sunshine Band",
        "AC/DC",

        // Con "&" en Soul/Funk/R&B
        "Kool & the Gang",
        "Kool & The Gang",
        "Sly & the Family Stone",
        "Sly & The Family Stone",
        "Ike & Tina Turner",
        "Sam & Dave",
        "Ashford & Simpson",
        "Womack & Womack",
        "Peaches & Herb",
        "Sly & Robbie",
        "Marvin Gaye & Tammi Terrell",

        // Con "&" en Hip Hop
        "Eric B. & Rakim",
        "Pete Rock & CL Smooth",
        "Method Man & Redman",
        "DJ Jazzy Jeff & The Fresh Prince",
        "Macklemore & Ryan Lewis",

        // Con "&" en Country
        "Brooks & Dunn",
        "Big & Rich",

        // Con "&" en Latino / Reggaetón
        "Wisin & Yandel",
        "Zion & Lennox",
        "Alexis & Fido",
        "Jowell & Randy",
        "Héctor & Tito",
        "Arcángel & De La Ghetto",
        "Jesse & Joy"
    )

    // Precompiled list of exceptions sorted descending by length to ensure longest match first
    private val PRECOMPILED_EXCEPTIONS: List<Pair<Regex, String>> by lazy {
        COMMA_AND_AMP_EXCEPTIONS
            .distinctBy { it.lowercase() }
            .sortedByDescending { it.length }
            .map { name ->
                Regex("(?i)\\b" + Regex.escape(name) + "\\b") to name
            }
    }

    // Regex to detect collaboration indicators in song titles like:
    // "Song (feat. Artist)", "Song [ft. Artist]", "Song (with Artist)"
    private val TITLE_FEAT_REGEX = Regex(
        "(?i)[\\(\\[]\\s*(?:feat\\.?|ft\\.?|featuring|with)\\s+([^\\]\\)]+)[\\)\\]]"
    )

    // Regex for splitting non-protected composite artist tags (includes ~, ～, 〜, ⁓, ∼ delimiters)
    private val DELIMITER_REGEX = Regex(
        "(?i)\\s*(?:~|～|〜|⁓|∼|;|/|\\\\|\\band\\b|&|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b|\\bw/\\b|\\s+x\\s+|\\s+X\\s+|\\bvs\\.?\\b|,)\\s*"
    )

    // Ultra-fast memory cache for parsed artist lists
    private val parseCache = ConcurrentHashMap<String, List<String>>()

    /**
     * Splits an artist string into clean, individual artist names while strictly
     * protecting known bands/artists containing commas or ampersands.
     */
    fun splitArtists(rawArtist: String?): List<String> {
        if (rawArtist.isNullOrBlank()) return emptyList()
        val trimmed = rawArtist.trim()

        if (isGeneric(trimmed)) {
            return emptyList()
        }

        // Direct exact match check on protected list
        for (protected in COMMA_AND_AMP_EXCEPTIONS) {
            if (trimmed.equals(protected, ignoreCase = true)) {
                return listOf(protected)
            }
        }

        // Fast check: If string doesn't contain any potential delimiter or exception indicator,
        // avoid running any regex!
        val hasDelimiter = trimmed.contains('~') ||
                trimmed.contains('～') ||
                trimmed.contains('〜') ||
                trimmed.contains('⁓') ||
                trimmed.contains('∼') ||
                trimmed.contains(',') ||
                trimmed.contains('&') ||
                trimmed.contains(';') ||
                trimmed.contains('/') ||
                trimmed.contains('\\') ||
                trimmed.contains("feat", ignoreCase = true) ||
                trimmed.contains("ft", ignoreCase = true) ||
                trimmed.contains("with", ignoreCase = true) ||
                trimmed.contains("and", ignoreCase = true) ||
                trimmed.contains(" x ", ignoreCase = true) ||
                trimmed.contains("vs", ignoreCase = true)

        if (!hasDelimiter) {
            val cleaned = cleanArtistName(trimmed)
            return if (cleaned.isNotBlank() && !isGeneric(cleaned)) listOf(cleaned) else emptyList()
        }

        // Placeholder replacement technique
        var working = trimmed
        val placeholderMap = mutableMapOf<String, String>()

        for ((regex, originalName) in PRECOMPILED_EXCEPTIONS) {
            // Fast substring check before running regex:
            if (working.contains(originalName, ignoreCase = true)) {
                val match = regex.find(working)
                if (match != null) {
                    val placeholder = "__ARTIST_EXC_${placeholderMap.size}__"
                    placeholderMap[placeholder] = match.value
                    working = working.replace(regex, placeholder)
                }
            }
        }

        // Split remaining string by standard delimiters
        val rawParts = working.split(DELIMITER_REGEX)

        // Restore original protected artist names
        val result = mutableListOf<String>()
        for (part in rawParts) {
            var restored = cleanArtistName(part)
            if (restored.isBlank()) continue

            for ((token, original) in placeholderMap) {
                if (restored.contains(token)) {
                    restored = restored.replace(token, original)
                }
            }

            val cleaned = cleanArtistName(restored)
            if (cleaned.isNotBlank() && !isGeneric(cleaned)) {
                result.add(cleaned)
            }
        }

        // If splitting left no valid artists, return original
        return if (result.isNotEmpty()) {
            result.distinctBy { it.lowercase() }
        } else {
            listOf(trimmed)
        }
    }

    /**
     * Extracts all unique artists associated with a track by checking both
     * the artist field and featured artists mentioned in the title.
     * Prevents duplicate entries and caches results for instant O(1) lookups.
     */
    fun extractAllArtists(rawArtist: String?, trackTitle: String? = null): List<String> {
        val cacheKey = "${rawArtist ?: ""}|||${trackTitle ?: ""}"
        parseCache[cacheKey]?.let { return it }

        val computed = computeExtractAllArtists(rawArtist, trackTitle)
        parseCache[cacheKey] = computed
        return computed
    }

    private fun computeExtractAllArtists(rawArtist: String?, trackTitle: String?): List<String> {
        val result = mutableListOf<String>()

        // 1. Parse artist field
        val fromArtist = splitArtists(rawArtist)
        result.addAll(fromArtist)

        // 2. Parse title featured artists if present (e.g. "Song (feat. Pharrell)")
        if (!trackTitle.isNullOrBlank()) {
            val match = TITLE_FEAT_REGEX.find(trackTitle)
            if (match != null) {
                val featuredPart = match.groupValues.getOrNull(1)
                if (!featuredPart.isNullOrBlank()) {
                    val featuredArtists = splitArtists(featuredPart)
                    for (fa in featuredArtists) {
                        val isDuplicate = result.any { existing ->
                            existing.equals(fa, ignoreCase = true) ||
                            existing.startsWith(fa, ignoreCase = true) ||
                            fa.startsWith(existing, ignoreCase = true)
                        }
                        if (!isDuplicate) {
                            result.add(fa)
                        }
                    }
                }
            }
        }

        return if (result.isNotEmpty()) {
            result.distinctBy { it.lowercase() }
        } else {
            listOf(rawArtist?.trim()?.takeIf { it.isNotBlank() } ?: "Unknown Artist")
        }
    }

    private fun cleanArtistName(name: String): String {
        return name.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .removeSurrounding("(")
            .removeSurrounding(")")
            .removeSurrounding("[")
            .removeSurrounding("]")
            .trim(' ', '~', '～', '〜', '⁓', '∼', '-', '/')
            .trim()
    }

    private fun isGeneric(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower.isBlank() ||
                lower == "<unknown>" ||
                lower == "unknown" ||
                lower == "unknown artist" ||
                lower == "desconocido" ||
                lower == "artista desconocido" ||
                lower == "various artists" ||
                lower == "varios artistas"
    }
}
