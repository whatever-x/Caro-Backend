package com.whatever.caro.nickname.internal

import com.whatever.caro.nickname.NicknameLocales
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import java.util.Locale

class WordDictionary {

    private val localeCategories: Map<String, Map<String, List<String>>> = loadAllLocales()

    fun randomWord(
        locale: Locale,
        category: String,
    ): String {
        val localeKey = resolveLocaleKey(locale)
        val categories = localeCategories[localeKey]
            ?: error("Locale resolved to '$localeKey' but no data loaded -- check SUPPORTED_LOCALES and resource files")
        val words = categories[category]
            ?: throw IllegalArgumentException("Unknown category: $category for locale: $localeKey")
        return words.random()
    }

    fun availableCategories(
        locale: Locale,
    ): Set<String> {
        val localeKey = resolveLocaleKey(locale)
        return localeCategories.getValue(localeKey).keys
    }

    private fun resolveLocaleKey(
        locale: Locale,
    ): String {
        val key = locale.language
        return if (localeCategories.containsKey(key)) {
            key
        } else {
            NicknameLocales.DEFAULT.language
        }
    }

    private fun loadAllLocales(): Map<String, Map<String, List<String>>> {
        val resolver = PathMatchingResourcePatternResolver()
        val result = mutableMapOf<String, Map<String, List<String>>>()

        for (locale in NicknameLocales.SUPPORTED) {
            val localeKey = locale.language
            val pattern = "classpath:nickname/$localeKey/*.txt"
            val resources = resolver.getResources(pattern)

            val categories = resources.associate { resource ->
                val category = requireNotNull(resource.filename) {
                    "Resource filename is null: $resource"
                }.removeSuffix(".txt")
                val words = resource.inputStream.bufferedReader().useLines { lines ->
                    lines.filter { it.isNotBlank() }.map { it.trim() }.toList()
                }
                require(words.isNotEmpty()) { "Word list is empty: nickname/$localeKey/${resource.filename}" }
                category to words
            }

            if (categories.isNotEmpty()) {
                result[localeKey] = categories
            }
        }

        return result
    }
}
