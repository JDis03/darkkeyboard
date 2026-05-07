package org.dark.keyboard
import java.util.Locale
open class LanguageSwitcher(val inputLocale: Locale = Locale.getDefault()) {
    fun getLocaleCount() = 1
    fun getSystemLocale() = Locale.getDefault()
    fun getNextInputLocale() = Locale.getDefault()
    fun getPrevInputLocale() = Locale.getDefault()
    companion object {
        @JvmStatic fun toTitleCase(s: String) = s.replaceFirstChar { it.uppercase() }
    }
}
