package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Result data holder containing artist biography and complementary metadata
 * resolved from TheAudioDB and Wikipedia.
 */
data class ArtistBioResult(
    val bio: String,
    val origin: String? = null,
    val fanartUrl: String? = null,
    val thumbUrl: String? = null,
    val diedYear: String? = null,
    val genre: String? = null,
    val source: String = "theaudiodb"
)

/**
 * Dual-API Artist Biography Architecture (TheAudioDB + Wikipedia with Anchor Terms + Last.fm)
 *
 * 1. TheAudioDB API (Primary):
 *    - Searches https://www.theaudiodb.com/api/v1/json/2/search.php?s={nombre_del_artista}
 *      (with fallback to public key 523532).
 *    - Filters returned artists for an exact case-insensitive match (a.strArtist.equalsIgnoreCase(artistName)).
 *    - Prioritizes Spanish biography (strBiographyES) over English (strBiographyEN / strBiography).
 *    - Extracts origin country, fanart, thumb, genre, and year of death (intDiedYear).
 *
 * 2. Wikipedia API (Respaldo en Español e Inglés con Término de Anclaje):
 *    - Si TheAudioDB no devuelve biografía, consulta Endpoint REST:
 *      https://es.wikipedia.org/api/rest_v1/page/summary/{nombre_del_artista}
 *    - Búsqueda Avanzada: Si el resultado es desambiguación, error o no musical, inyecta
 *      explícitamente " cantante" en la Action API:
 *      https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch={nombre_del_artista + ' cantante'}
 *      Toma el mejor resultado y consulta nuevamente el Endpoint REST con el título exacto.
 *    - Endpoint REST en Inglés: Si no se encuentra en español, realiza la búsqueda en inglés.
 *
 * 3. Last.fm Music Encyclopedia (Respaldo para artistas indie/electrónicos no listados en Wikipedia):
 *    - Obtiene la biografía musical limpia especializada (e.g. Kotori - Healing).
 */
class ArtistBioRepository {
    companion object {
        private val instance = ArtistBioRepository()

        suspend fun getArtistBiography(artistName: String): String? =
            instance.getArtistBiographyResult(artistName)?.bio

        suspend fun getArtistBiographyResult(artistName: String): ArtistBioResult? =
            instance.getArtistBiographyResult(artistName)

        fun isNonMusicDefinition(text: String?, description: String? = null): Boolean =
            instance.isNonMusicDefinition(text, description)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val musicKeywords = listOf(
        "banda", "música", "musica", "music", "músico", "musico", "cantante", "singer",
        "álbum", "album", "canción", "cancion", "song", "rock", "pop", "hip hop",
        "metal", "jazz", "grupo", "band", "vocalista", "guitarrista", "compositor",
        "cantautor", "discografía", "discografia", "sencillo", "single", "orquesta",
        "discográfica", "gira", "concierto", "baterista", "bajista", "rap", "trap",
        "reggaetón", "reggaeton", "solista", "productor", "discográfico", "producer",
        "dj", "dubstep", "electronic", "beatmaker", "record label"
    )

    suspend fun getArtistBiographyResult(artistName: String): ArtistBioResult? = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank() || isGenericArtist(cleanArtist)) {
            return@withContext null
        }

        try {
            // ==================== 1. THEAUDIODB API (PRIMARY) ====================
            val audioDbResult = fetchTheAudioDB(cleanArtist)
            if (audioDbResult != null && !audioDbResult.bio.isNullOrBlank() && !isNonMusicDefinition(audioDbResult.bio)) {
                return@withContext audioDbResult
            }

            // ==================== 2. WIKIPEDIA API (ESPAÑOL RESPALDO) ====================
            val encodedName = URLEncoder.encode(cleanArtist, "UTF-8")
            var esUrl = "https://es.wikipedia.org/api/rest_v1/page/summary/$encodedName"
            var esResponse = fetchJson(esUrl)

            var esType = esResponse?.optString("type")
            var esTitle = esResponse?.optString("title")
            var esDesc = esResponse?.optString("description")
            var esExtract = esResponse?.optString("extract")

            val needsAdvancedSearch = esType == "disambiguation" ||
                    esTitle == "Not found" ||
                    esType?.contains("not_found", ignoreCase = true) == true ||
                    esResponse == null ||
                    esExtract.isNullOrBlank() ||
                    isNonMusicDefinition(esExtract, esDesc)

            // Búsqueda Avanzada con término de anclaje " cantante"
            if (needsAdvancedSearch) {
                val anchorQueries = listOf("$cleanArtist cantante", "$cleanArtist banda", "$cleanArtist músico")
                for (query in anchorQueries) {
                    val searchName = URLEncoder.encode(query, "UTF-8")
                    val searchUrl = "https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=$searchName&utf8=&format=json&origin=*"
                    val searchJson = fetchJson(searchUrl)
                    val searchResults = searchJson?.optJSONObject("query")?.optJSONArray("search")

                    if (searchResults != null && searchResults.length() > 0) {
                        for (i in 0 until minOf(searchResults.length(), 3)) {
                            val candidateTitle = searchResults.getJSONObject(i).optString("title")
                            if (candidateTitle.isNotBlank() && titleMatchesArtist(candidateTitle, cleanArtist)) {
                                val encodedBestTitle = URLEncoder.encode(candidateTitle, "UTF-8")
                                val refinedUrl = "https://es.wikipedia.org/api/rest_v1/page/summary/$encodedBestTitle"
                                val refinedJson = fetchJson(refinedUrl)
                                val candidateType = refinedJson?.optString("type")
                                val candidateDesc = refinedJson?.optString("description")
                                val candidateExtract = refinedJson?.optString("extract")

                                if (refinedJson != null &&
                                    !candidateExtract.isNullOrBlank() &&
                                    candidateType != "disambiguation" &&
                                    !isNonMusicDefinition(candidateExtract, candidateDesc)
                                ) {
                                    esResponse = refinedJson
                                    esExtract = candidateExtract
                                    break
                                }
                            }
                        }
                        if (esExtract != null && !isNonMusicDefinition(esExtract)) {
                            break
                        }
                    }
                }
            }

            val finalEsType = esResponse?.optString("type")
            val finalEsDesc = esResponse?.optString("description")
            val finalEsExtract = esResponse?.optString("extract")
            if (esResponse != null &&
                !finalEsExtract.isNullOrBlank() &&
                finalEsType != "disambiguation" &&
                !isNonMusicDefinition(finalEsExtract, finalEsDesc)
            ) {
                return@withContext ArtistBioResult(
                    bio = sanitizeExtract(finalEsExtract),
                    origin = audioDbResult?.origin,
                    fanartUrl = audioDbResult?.fanartUrl,
                    thumbUrl = audioDbResult?.thumbUrl,
                    diedYear = audioDbResult?.diedYear,
                    genre = audioDbResult?.genre,
                    source = "wikipedia_es"
                )
            }

            // ==================== 3. WIKIPEDIA API (INGLÉS RESPALDO) ====================
            val enBio = fetchEnglishWikipediaBio(cleanArtist)
            if (!enBio.isNullOrBlank()) {
                return@withContext ArtistBioResult(
                    bio = sanitizeExtract(enBio),
                    origin = audioDbResult?.origin,
                    fanartUrl = audioDbResult?.fanartUrl,
                    thumbUrl = audioDbResult?.thumbUrl,
                    diedYear = audioDbResult?.diedYear,
                    genre = audioDbResult?.genre,
                    source = "wikipedia_en"
                )
            }

            // ==================== 4. LAST.FM MUSIC ENCYCLOPEDIA (NICHE/EDM FALLBACK) ====================
            val lastFmBio = fetchLastFmBio(cleanArtist)
            if (!lastFmBio.isNullOrBlank()) {
                return@withContext ArtistBioResult(
                    bio = sanitizeExtract(lastFmBio),
                    origin = audioDbResult?.origin,
                    fanartUrl = audioDbResult?.fanartUrl,
                    thumbUrl = audioDbResult?.thumbUrl,
                    diedYear = audioDbResult?.diedYear,
                    genre = audioDbResult?.genre,
                    source = "lastfm"
                )
            }

            // If audioDb had metadata without bio (e.g. country or fanart), still return it
            if (audioDbResult != null && (!audioDbResult.origin.isNullOrBlank() || !audioDbResult.fanartUrl.isNullOrBlank())) {
                return@withContext audioDbResult
            }

            return@withContext null
        } catch (e: Exception) {
            Log.e("ArtistBioRepository", "Error resolving biography for '$cleanArtist': ${e.message}")
            return@withContext null
        }
    }

    /**
     * Consults TheAudioDB API and matches iteratively ensuring exact case-insensitive artist name match.
     */
    private fun fetchTheAudioDB(artistName: String): ArtistBioResult? {
        val encoded = URLEncoder.encode(artistName, "UTF-8")
        // Try endpoint 2 as requested, with fallback to public key 523532
        val keys = listOf("2", "523532")
        for (key in keys) {
            try {
                val url = "https://www.theaudiodb.com/api/v1/json/$key/search.php?s=$encoded"
                val json = fetchJson(url) ?: continue
                val artists = json.optJSONArray("artists") ?: continue

                val artistsList = mutableListOf<JSONObject>()
                for (i in 0 until artists.length()) {
                    artistsList.add(artists.getJSONObject(i))
                }

                // Coincidencia exacta o primer resultado como en Fuzion-Player
                val exactArtist = artistsList.find {
                    it.optString("strArtist", "").trim().equals(artistName.trim(), ignoreCase = true)
                } ?: artistsList.firstOrNull()

                if (exactArtist != null) {
                    val bioES = exactArtist.optString("strBiographyES").takeIf { it.isNotBlank() }
                    val bioEN = exactArtist.optString("strBiographyEN").takeIf { it.isNotBlank() }
                        ?: exactArtist.optString("strBiography").takeIf { it.isNotBlank() }

                    // Dar prioridad a español strBiographyES, o inglés strBiography
                    val chosenBio = bioES ?: bioEN
                    val country = exactArtist.optString("strCountry").takeIf { it.isNotBlank() }
                    val fanart = exactArtist.optString("strArtistFanart").takeIf { it.isNotBlank() }
                    val thumb = exactArtist.optString("strArtistThumb").takeIf { it.isNotBlank() }
                    val diedYear = exactArtist.optString("intDiedYear").takeIf { it.isNotBlank() && it != "null" }
                    val genre = exactArtist.optString("strGenre").takeIf { it.isNotBlank() }

                    return ArtistBioResult(
                        bio = chosenBio ?: "",
                        origin = country,
                        fanartUrl = fanart,
                        thumbUrl = thumb,
                        diedYear = diedYear,
                        genre = genre,
                        source = "theaudiodb"
                    )
                }
            } catch (e: Exception) {
                // Continue to next key or fallback
            }
        }
        return null
    }

    private fun fetchEnglishWikipediaBio(artistName: String): String? {
        return try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedName"
            var responseJson = fetchJson(url)

            var type = responseJson?.optString("type")
            var title = responseJson?.optString("title")
            var desc = responseJson?.optString("description")
            var extract = responseJson?.optString("extract")

            val needsRefinement = type == "disambiguation" ||
                    title == "Not found" ||
                    type?.contains("not_found", ignoreCase = true) == true ||
                    responseJson == null ||
                    extract.isNullOrBlank() ||
                    isNonMusicDefinition(extract, desc)

            if (needsRefinement) {
                val anchorTerms = listOf("singer", "band", "musician", "music", "producer")
                for (anchor in anchorTerms) {
                    val searchName = URLEncoder.encode("$artistName $anchor", "UTF-8")
                    val searchUrl = "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$searchName&utf8=&format=json&origin=*"
                    val searchJson = fetchJson(searchUrl)
                    val searchResults = searchJson?.optJSONObject("query")?.optJSONArray("search")
                    if (searchResults != null && searchResults.length() > 0) {
                        for (i in 0 until minOf(searchResults.length(), 3)) {
                            val candidateTitle = searchResults.getJSONObject(i).optString("title")
                            if (candidateTitle.isNotBlank() && titleMatchesArtist(candidateTitle, artistName)) {
                                val encodedBestTitle = URLEncoder.encode(candidateTitle, "UTF-8")
                                val refinedUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedBestTitle"
                                val refinedJson = fetchJson(refinedUrl)
                                val candidateType = refinedJson?.optString("type")
                                val candidateDesc = refinedJson?.optString("description")
                                val candidateExtract = refinedJson?.optString("extract")

                                if (refinedJson != null &&
                                    !candidateExtract.isNullOrBlank() &&
                                    candidateType != "disambiguation" &&
                                    !isNonMusicDefinition(candidateExtract, candidateDesc)
                                ) {
                                    responseJson = refinedJson
                                    extract = candidateExtract
                                    break
                                }
                            }
                        }
                        if (extract != null && !isNonMusicDefinition(extract)) {
                            break
                        }
                    }
                }
            }

            val finalType = responseJson?.optString("type")
            val finalDesc = responseJson?.optString("description")
            val finalExtract = responseJson?.optString("extract")
            if (responseJson != null &&
                !finalExtract.isNullOrBlank() &&
                finalType != "disambiguation" &&
                !isNonMusicDefinition(finalExtract, finalDesc)
            ) {
                finalExtract
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchLastFmBio(artistName: String): String? {
        return try {
            val encodedName = URLEncoder.encode(artistName, "UTF-8")
            val url = "https://ws.audioscrobbler.com/2.0/?method=artist.getinfo&artist=$encodedName&api_key=b25b959554ed76058ac220b7b2e0a026&format=json"
            val json = fetchJson(url) ?: return null
            val rawBio = json.optJSONObject("artist")?.optJSONObject("bio")?.optString("summary")
            if (rawBio.isNullOrBlank()) return null

            var cleanText = rawBio.replace(Regex("<[^>]*>"), "")
                .replace("Read more on Last.fm", "")
                .replace("User-contributed text is available under the Creative Commons By-SA License; additional terms may apply.", "")
                .trim()

            val listMatch = Pattern.compile("(?:There are multiple artists|There are at least|Hay varios artistas).*?[1-9]\\)\\s*(.*)", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(cleanText)
            if (listMatch.find()) {
                val candidate = listMatch.group(1)?.trim() ?: ""
                val split = candidate.split(Regex("\\n\\s*[2-9]\\)|\\s+[2-9]\\)\\s+"))
                if (split.isNotEmpty() && split[0].trim().length > 30) {
                    cleanText = split[0].trim()
                }
            }

            if (cleanText.length > 25 && !isNonMusicDefinition(cleanText)) {
                cleanText
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun isNonMusicDefinition(text: String?, description: String? = null): Boolean {
        if (text.isNullOrBlank()) return true
        val combined = ((description ?: "") + " " + text).lowercase().trim()

        if (combined.startsWith("searched for") || combined.contains("searched for \"")) return true
        if (combined.contains("puede referirse a:") || combined.contains("may refer to:")) return true
        if (combined.contains("desambiguación") || combined.contains("disambiguation")) return true

        if (combined.contains("given name") || combined.contains("nombre de pila") || combined.contains("nombre propio")) return true
        if (combined.contains("feminine japanese given name") || combined.contains("masculine given name") || combined.contains("feminine given name")) return true
        if (combined.contains("notable people with the name") || combined.contains("people with the name include")) return true
        if (combined.contains("personas notables con este nombre") || combined.contains("personas con este nombre")) return true
        if (combined.contains("surname") || combined.contains("apellido") || combined.contains("family name")) return true
        if (combined.contains("name list") || combined.contains("lista de nombres")) return true

        if (combined.contains("videojuego") || combined.contains("video game") || combined.contains("fictional character") || combined.contains("personaje de ficción")) return true

        if (combined.startsWith("a tool is a device") || combined.startsWith("tool is a device")) return true
        if (combined.contains("herramienta de mano") || combined.contains("dispositivo o instrumento que se utiliza")) return true
        if (combined.contains("primer libro de la biblia") || combined.contains("primer libro del pentateuco")) return true
        if (combined.contains("libro del génesis") || combined.contains("libro de génesis")) return true
        if (combined.contains("paraje de un desierto") || combined.contains("oasis es un paraje")) return true

        val hasMusicKeyword = musicKeywords.any { combined.contains(it) }
        if (!hasMusicKeyword) {
            return true
        }

        return false
    }

    private fun titleMatchesArtist(title: String, artist: String): Boolean {
        val tLower = title.lowercase()
        val aLower = artist.lowercase().trim()
        if (tLower.contains(aLower)) return true
        val words = aLower.split(Regex("\\s+")).filter { it.length > 2 }
        return words.any { tLower.contains(it) }
    }

    private fun sanitizeExtract(extract: String): String {
        return extract.trim()
    }

    private fun isGenericArtist(name: String): Boolean {
        val lower = name.lowercase().trim()
        return lower == "unknown artist" ||
                lower == "unknown" ||
                lower == "<unknown>" ||
                lower == "desconocido" ||
                lower == "artista desconocido" ||
                lower == "various artists" ||
                lower == "varios artistas"
    }

    private fun fetchJson(url: String): JSONObject? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "LiquidMusicPlayer/1.0 (Android; music-bio@example.com)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 404) return null
                val body = response.body?.string()
                if (body != null) JSONObject(body) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
