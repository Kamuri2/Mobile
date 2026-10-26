package com.example.util

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

data class GoogleFontMeta(
    val familyName: String,
    val category: String, // Sans-Serif, Serif, Display, Handwriting, Monospace
    val isBundled: Boolean = false
)

object GoogleFontsManager {

    private const val TTF_USER_AGENT = "Wget/1.18"
    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 12000

    val BUNDLED_FONTS = listOf(
        GoogleFontMeta("Outfit", "Sans-Serif", true),
        GoogleFontMeta("Poppins", "Sans-Serif", true),
        GoogleFontMeta("Montserrat", "Sans-Serif", true),
        GoogleFontMeta("Inter", "Sans-Serif", true),
        GoogleFontMeta("Roboto", "Sans-Serif", true),
        GoogleFontMeta("Lato", "Sans-Serif", true),
        GoogleFontMeta("Raleway", "Sans-Serif", true),
        GoogleFontMeta("Nunito", "Sans-Serif", true),
        GoogleFontMeta("Playfair Display", "Serif", true),
        GoogleFontMeta("Cinzel", "Serif", true),
        GoogleFontMeta("Oswald", "Display", true),
        GoogleFontMeta("Bebas Neue", "Display", true),
        GoogleFontMeta("Lobster", "Display", true),
        GoogleFontMeta("Pacifico", "Handwriting", true),
        GoogleFontMeta("Caveat", "Handwriting", true),
        GoogleFontMeta("Dancing Script", "Handwriting", true),
        GoogleFontMeta("Fira Code", "Monospace", true)
    )

    // Curated catalog of the most popular Google Fonts
    val CATALOG_FONTS = listOf(
        GoogleFontMeta("Open Sans", "Sans-Serif"),
        GoogleFontMeta("Rubik", "Sans-Serif"),
        GoogleFontMeta("Quicksand", "Sans-Serif"),
        GoogleFontMeta("Comfortaa", "Display"),
        GoogleFontMeta("Space Grotesk", "Sans-Serif"),
        GoogleFontMeta("Syne", "Display"),
        GoogleFontMeta("Anton", "Display"),
        GoogleFontMeta("Righteous", "Display"),
        GoogleFontMeta("Abril Fatface", "Display"),
        GoogleFontMeta("Bangers", "Display"),
        GoogleFontMeta("Lora", "Serif"),
        GoogleFontMeta("Merriweather", "Serif"),
        GoogleFontMeta("EB Garamond", "Serif"),
        GoogleFontMeta("Great Vibes", "Handwriting"),
        GoogleFontMeta("Alex Brush", "Handwriting"),
        GoogleFontMeta("Satisfy", "Handwriting"),
        GoogleFontMeta("Shadows Into Light", "Handwriting"),
        GoogleFontMeta("Permanent Marker", "Handwriting"),
        GoogleFontMeta("JetBrains Mono", "Monospace"),
        GoogleFontMeta("Source Code Pro", "Monospace"),
        GoogleFontMeta("Inconsolata", "Monospace")
    )

    fun sanitizeFontResourceName(fontFamily: String): String {
        var name = fontFamily.lowercase().trim()
        name = name.replace(Regex("[^a-z0-9]+"), "_").trim('_')
        if (name.isBlank()) name = "custom_font"
        else if (name[0].isDigit()) name = "f_$name"
        return name
    }

    fun getFontDirectory(context: Context): File {
        val dir = File(context.filesDir, "google_fonts")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getCustomFontFile(context: Context, fontFamily: String): File {
        val filename = "${sanitizeFontResourceName(fontFamily)}.ttf"
        return File(getFontDirectory(context), filename)
    }

    fun isFontInstalledLocally(context: Context, fontFamily: String): Boolean {
        if (fontFamily.equals("System Font (Default)", ignoreCase = true)) return true
        if (BUNDLED_FONTS.any { it.familyName.equals(fontFamily, ignoreCase = true) }) return true
        val file = getCustomFontFile(context, fontFamily)
        return file.exists() && file.length() > 500L
    }

    fun getDownloadedFonts(context: Context): List<String> {
        val dir = getFontDirectory(context)
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".ttf") } ?: return emptyList()
        return files.map { file ->
            file.nameWithoutSuffix()
                .split("_")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
    }

    private fun File.nameWithoutSuffix(): String {
        return name.substringBeforeLast(".")
    }

    /**
     * Resolves and downloads font binary from Google Fonts CSS API.
     */
    suspend fun downloadFontFromGoogle(context: Context, fontFamily: String): Result<File> = withContext(Dispatchers.IO) {
        try {
            val targetFile = getCustomFontFile(context, fontFamily)
            if (targetFile.exists() && targetFile.length() > 1000L) {
                return@withContext Result.success(targetFile)
            }

            // 1. Fetch CSS with TTF link
            val encoded = URLEncoder.encode(fontFamily.trim(), "UTF-8")
            val cssUrl = "https://fonts.googleapis.com/css?family=$encoded"
            val conn = (URL(cssUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", TTF_USER_AGENT)
                instanceFollowRedirects = true
            }

            val responseCode = conn.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("Google Font '$fontFamily' no fue encontrado (HTTP $responseCode)"))
            }

            val cssContent = conn.inputStream.bufferedReader().use { it.readText() }
            val matcher = Pattern.compile("url\\(\\s*['\"]?(https?://[^)'\"]+)['\"]?\\s*\\)").matcher(cssContent)
            if (!matcher.find()) {
                return@withContext Result.failure(Exception("No se encontró el archivo TTF para '$fontFamily'"))
            }

            val ttfUrl = matcher.group(1) ?: return@withContext Result.failure(Exception("URL vacía para '$fontFamily'"))

            // 2. Download TTF payload
            val ttfConn = (URL(ttfUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", TTF_USER_AGENT)
                instanceFollowRedirects = true
            }

            val tempFile = File(getFontDirectory(context), "${targetFile.name}.tmp")
            ttfConn.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.length() < 500L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("Descarga incompleta de '$fontFamily'"))
            }

            // Verify typeface validity
            try {
                val typeface = Typeface.createFromFile(tempFile)
                if (typeface == null) {
                    tempFile.delete()
                    return@withContext Result.failure(Exception("El archivo descargado no es un formato de fuente válido"))
                }
            } catch (e: Exception) {
                tempFile.delete()
                return@withContext Result.failure(e)
            }

            if (targetFile.exists()) targetFile.delete()
            tempFile.renameTo(targetFile)

            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolves dynamic FontFamily for any Google Font (bundled or downloaded).
     */
    fun loadFontFamily(context: Context?, fontFamily: String): FontFamily {
        val clean = fontFamily.trim()
        if (clean.isBlank() || clean.startsWith("System", ignoreCase = true)) {
            return FontFamily.Default
        }

        // Check bundled
        val bundled = getBundledFontFamily(clean)
        if (bundled != null) return bundled

        // Check downloaded
        if (context != null) {
            val file = getCustomFontFile(context, clean)
            if (file.exists() && file.length() > 500L) {
                try {
                    val typeface = Typeface.createFromFile(file)
                    if (typeface != null) {
                        return FontFamily(typeface)
                    }
                } catch (_: Exception) {}
            }
        }

        return FontFamily.Default
    }

    private fun getBundledFontFamily(fontName: String): FontFamily? {
        val normalized = sanitizeFontResourceName(fontName)
        return when (normalized) {
            "outfit" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.outfit))
            "poppins" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.poppins))
            "montserrat" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.montserrat))
            "inter" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.inter))
            "roboto" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.roboto))
            "lato" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.lato))
            "oswald" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.oswald))
            "raleway" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.raleway))
            "pacifico" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.pacifico))
            "playfair_display", "playfair" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.playfair_display))
            "bebas_neue" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.bebas_neue))
            "caveat" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.caveat))
            "dancing_script" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.dancing_script))
            "fira_code" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.fira_code))
            "lobster" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.lobster))
            "nunito" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.nunito))
            "cinzel" -> FontFamily(androidx.compose.ui.text.font.Font(com.example.R.font.cinzel))
            else -> null
        }
    }
}
