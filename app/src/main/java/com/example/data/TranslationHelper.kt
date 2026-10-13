package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight translation service for artist biographies and external metadata.
 * Translates texts to the target app language without requiring paid API keys,
 * using reliable Google Translate endpoint with smart caching and language detection.
 */
object TranslationHelper {
    private const val TAG = "TranslationHelper"

    // In-memory cache for translated strings: Key is "${targetLangCode}_${textHash}"
    private val translationCache = mutableMapOf<String, String>()

    /**
     * Map app language representation (e.g. "Spanish", "Español", "es", "English", "Japanese", "ja")
     * to a standard 2-letter ISO 639-1 code.
     */
    fun mapToLanguageCode(lang: String): String {
        val clean = lang.trim().lowercase()
        return when {
            clean.startsWith("es") || clean.contains("span") || clean.contains("español") -> "es"
            clean.startsWith("en") || clean.contains("engl") || clean.contains("inglés") || clean.contains("ingles") -> "en"
            clean.startsWith("ja") || clean.contains("japan") || clean.contains("japon") || clean.contains("日本語") -> "ja"
            clean.startsWith("fr") || clean.contains("fren") || clean.contains("franc") || clean.contains("français") -> "fr"
            clean.startsWith("de") || clean.contains("germ") || clean.contains("alem") || clean.contains("deutsch") -> "de"
            clean.startsWith("it") || clean.contains("ital") -> "it"
            clean.startsWith("pt") || clean.contains("port") || clean.contains("portugu") -> "pt"
            else -> "en"
        }
    }

    /**
     * Fast heuristic to detect if the text is already in the target language.
     * Prevents unnecessary API calls when text already matches.
     */
    fun isAlreadyInTargetLanguage(text: String, targetLangCode: String): Boolean {
        if (text.isBlank()) return true
        val lower = text.lowercase()

        when (targetLangCode) {
            "es" -> {
                // Typical Spanish connector words and characters
                val spanishMarkers = listOf(" es un ", " es una ", " nació en ", " nacido ", " de la ", " de los ", " y su ", " canciones ", " música ", " cantante ", " banda ")
                val hasSpanishChars = lower.any { it in "áéíóúüñ¿¡" }
                val hasSpanishWords = spanishMarkers.any { lower.contains(it) }
                return hasSpanishChars || hasSpanishWords
            }
            "en" -> {
                // Typical English connector words
                val englishMarkers = listOf(" is an ", " is a ", " was born ", " from ", " of the ", " their ", " and the ", " songs ", " music ", " singer ", " band ")
                val englishCount = englishMarkers.count { lower.contains(it) }
                return englishCount >= 2
            }
            "ja" -> {
                // Japanese Hiragana/Katakana/Kanji characters
                val hasJapaneseChars = text.any { ch ->
                    (ch in '\u3040'..'\u309F') || // Hiragana
                    (ch in '\u30A0'..'\u30FF') || // Katakana
                    (ch in '\u4E00'..'\u9FAF')    // Kanji
                }
                return hasJapaneseChars
            }
            "fr" -> {
                val frenchMarkers = listOf(" est un ", " est une ", " né en ", " née en ", " de la ", " des ", " et sa ", " chanson ", " musique ")
                return frenchMarkers.any { lower.contains(it) }
            }
            "de" -> {
                val germanMarkers = listOf(" ist ein ", " ist eine ", " wurde in ", " gebürtige ", " und seine ", " musik ", " sänger ")
                return germanMarkers.any { lower.contains(it) }
            }
            "it" -> {
                val italianMarkers = listOf(" è un ", " è una ", " nato a ", " nata a ", " della ", " e la sua ", " canzoni ", " musica ")
                return italianMarkers.any { lower.contains(it) }
            }
            "pt" -> {
                val portugueseMarkers = listOf(" é um ", " é uma ", " nasceu em ", " da ", " e sua ", " canções ", " música ", " cantor ")
                return portugueseMarkers.any { lower.contains(it) }
            }
        }
        return false
    }

    /**
     * Translates the provided bio/text to the app's target language.
     * If the text is already in the target language, it returns the text unmodified immediately.
     */
    suspend fun translateText(text: String?, targetLangNameOrCode: String): String? {
        if (text.isNullOrBlank()) return text
        val targetCode = mapToLanguageCode(targetLangNameOrCode)

        // 1. If text is already in target language, do NOT modify
        if (isAlreadyInTargetLanguage(text, targetCode)) {
            return text
        }

        // 2. Check cache
        val cacheKey = "${targetCode}_${text.hashCode()}"
        synchronized(translationCache) {
            val cached = translationCache[cacheKey]
            if (cached != null) return cached
        }

        return withContext(Dispatchers.IO) {
            try {
                // Split long text into chunks if > 1500 chars to avoid URL length issues
                val chunks = text.chunked(1200)
                val translatedChunks = mutableListOf<String>()

                for (chunk in chunks) {
                    val encoded = URLEncoder.encode(chunk, "UTF-8")
                    val urlString = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$targetCode&dt=t&q=$encoded"
                    val url = URL(urlString)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        connectTimeout = 4000
                        readTimeout = 4000
                    }

                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val jsonArray = JSONArray(response)
                        val sentencesArray = jsonArray.optJSONArray(0)
                        val chunkBuilder = StringBuilder()
                        if (sentencesArray != null) {
                            for (i in 0 until sentencesArray.length()) {
                                val sentence = sentencesArray.optJSONArray(i)
                                val translatedSentence = sentence?.optString(0) ?: ""
                                chunkBuilder.append(translatedSentence)
                            }
                        }
                        val resultChunk = chunkBuilder.toString()
                        if (resultChunk.isNotBlank()) {
                            translatedChunks.add(resultChunk)
                        } else {
                            translatedChunks.add(chunk)
                        }
                    } else {
                        translatedChunks.add(chunk)
                    }
                }

                val finalTranslation = translatedChunks.joinToString(" ")
                if (finalTranslation.isNotBlank()) {
                    synchronized(translationCache) {
                        translationCache[cacheKey] = finalTranslation
                    }
                    finalTranslation
                } else {
                    text
                }
            } catch (e: Exception) {
                Log.w(TAG, "Translation error: ${e.message}")
                text // Return original text on failure
            }
        }
    }
}
