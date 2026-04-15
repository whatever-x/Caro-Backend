package com.whatever.caro.nickname.internal

import com.whatever.caro.nickname.NicknameApi
import com.whatever.caro.nickname.NicknameBuilder
import com.whatever.caro.nickname.NicknameLocales
import org.springframework.stereotype.Component
import java.util.Locale

@Component
class NicknameService : NicknameApi {

    private val wordDictionary = WordDictionary()

    override fun create(
        locale: Locale,
    ): NicknameBuilder = NicknameBuilderImpl(wordDictionary, locale)

    override fun randomName(
        locale: Locale,
        separator: String,
    ): String = create(locale).adjective().anyNoun().withSeparator(separator).get()
}

private class NicknameBuilderImpl(
    private val wordDictionary: WordDictionary,
    private val locale: Locale = NicknameLocales.DEFAULT,
    private val parts: List<String> = emptyList(),
    private val separator: String = "_",
) : NicknameBuilder {

    override fun adjective(): NicknameBuilder = appendCategory(ADJECTIVES)

    override fun animal(): NicknameBuilder = appendCategory(ANIMALS)

    override fun color(): NicknameBuilder = appendCategory(COLORS)

    override fun noun(): NicknameBuilder = appendCategory(NOUNS)

    override fun anyNoun(): NicknameBuilder = appendCategory(ANY_NOUN)

    override fun person(): NicknameBuilder = appendCategory(PERSONS)

    override fun pokemon(): NicknameBuilder = appendCategory(POKEMONS)

    override fun superhero(): NicknameBuilder = appendCategory(SUPERHEROES)

    override fun withSeparator(
        separator: String,
    ): NicknameBuilder = NicknameBuilderImpl(wordDictionary, locale, parts, separator)

    override fun get(): String {
        require(parts.isNotEmpty()) { "At least one word category must be specified." }
        return parts.joinToString(separator) { category ->
            val resolved = if (category == ANY_NOUN) {
                NOUN_CATEGORIES.random()
            } else {
                category
            }

            wordDictionary.randomWord(locale, resolved)
        }
    }

    private fun appendCategory(
        category: String,
    ): NicknameBuilder = NicknameBuilderImpl(wordDictionary, locale, parts + category, separator)

    companion object {
        private const val ADJECTIVES = "adjectives"
        private const val ANIMALS = "animals"
        private const val COLORS = "colors"
        private const val NOUNS = "nouns"
        private const val PERSONS = "persons"
        private const val POKEMONS = "pokemons"
        private const val SUPERHEROES = "superheroes"
        private const val ANY_NOUN = "__any_noun__"
        private val NOUN_CATEGORIES = listOf(NOUNS, ANIMALS, PERSONS, POKEMONS, SUPERHEROES)
    }
}
