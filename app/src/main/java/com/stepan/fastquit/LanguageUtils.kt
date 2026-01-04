package com.stepan.fastquit

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

object LanguageUtils {

    // Define your supported languages here
    // Display Name -> ISO Code
    val supportedLanguages = mapOf(
        "English" to "en",
        "Čeština" to "cs"
    )

    fun setLanguage(languageCode: String) {
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(appLocale)
    }

    fun getCurrentLanguageCode(): String {
        val currentAppLocales = AppCompatDelegate.getApplicationLocales()
        // If empty, it follows the system. We return the first tag or "en" as fallback.
        return if (!currentAppLocales.isEmpty) {
            currentAppLocales.get(0)?.language ?: "en"
        } else {
            // If strictly following system, you might want to return Locale.getDefault().language
            // But for UI selection purposes, "en" or "system" is usually fine.
            Locale.getDefault().language
        }
    }

    // Helper to get the display name from the code
    fun getDisplayLanguage(code: String): String {
        return supportedLanguages.entries.find { it.value == code }?.key ?: "English"
    }
}