package com.example.ui

import android.content.Context
import android.content.res.Resources
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageManager {

    /**
     * Reads the device's default locale from Android system configuration.
     * Returns the normalized language name supported by the app (e.g. "Japanese", "English", "Spanish", etc.)
     */
    fun getDeviceDefaultLanguage(context: Context? = null): String {
        val systemLocale: Locale = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val systemLocales = Resources.getSystem().configuration.locales
                if (!systemLocales.isEmpty) {
                    systemLocales.get(0)
                } else {
                    context?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale
                    ?: context?.resources?.configuration?.locale
                    ?: Locale.getDefault()
            }
        } catch (_: Exception) {
            Locale.getDefault()
        } ?: Locale.getDefault()

        val langCode = systemLocale.language.lowercase()
        return getLanguageNameFromCode(langCode)
    }

    /**
     * Reads the device's default 2-letter language code (e.g. "ja", "en", "es").
     */
    fun getDeviceDefaultLanguageCode(context: Context? = null): String {
        val systemLocale: Locale = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val systemLocales = Resources.getSystem().configuration.locales
                if (!systemLocales.isEmpty) {
                    systemLocales.get(0)
                } else {
                    context?.resources?.configuration?.locales?.get(0) ?: Locale.getDefault()
                }
            } else {
                @Suppress("DEPRECATION")
                Resources.getSystem().configuration.locale
                    ?: context?.resources?.configuration?.locale
                    ?: Locale.getDefault()
            }
        } catch (_: Exception) {
            Locale.getDefault()
        } ?: Locale.getDefault()

        return systemLocale.language.lowercase()
    }

    /**
     * Map language name (stored in settings) to language tag (e.g. "Spanish" -> "es", "System" -> system code)
     */
    fun getLanguageCode(languageName: String): String {
        return when (languageName.trim().lowercase()) {
            "system", "sistema", "system default", "システム", "システムデフォルト", "" -> "system"
            "spanish", "español", "es" -> "es"
            "french", "français", "fr" -> "fr"
            "german", "deutsch", "de" -> "de"
            "italian", "italiano", "it" -> "it"
            "portuguese", "português", "pt" -> "pt"
            "japanese", "日本語", "ja" -> "ja"
            else -> "en"
        }
    }

    fun getDeviceSystemLanguage(): String = getDeviceDefaultLanguage()

    fun getDeviceSystemLanguageCode(): String = getDeviceDefaultLanguageCode()

    /**
     * Map language tag to standard display name
     */
    fun getLanguageNameFromCode(code: String): String {
        return when (code.trim().lowercase().take(2)) {
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "it" -> "Italian"
            "pt" -> "Portuguese"
            "ja" -> "Japanese"
            else -> "English"
        }
    }

    /**
     * Official AndroidX Per-App Language Preferences implementation
     * using AppCompatDelegate.setApplicationLocales
     */
    fun cambiarIdioma(codigoIdioma: String) {
        if (codigoIdioma.equals("system", ignoreCase = true)) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
        } else {
            val appLocale = LocaleListCompat.forLanguageTags(codigoIdioma)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    /**
     * Retrieve the currently saved locale from AppCompatDelegate / system
     */
    fun getActiveLanguage(fallback: String = "English"): String {
        val appLocales = AppCompatDelegate.getApplicationLocales()
        if (!appLocales.isEmpty) {
            val first = appLocales.get(0)
            if (first != null) {
                return getLanguageNameFromCode(first.language)
            }
        }
        return getDeviceDefaultLanguage()
    }
}
