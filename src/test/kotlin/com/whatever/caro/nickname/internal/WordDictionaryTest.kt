package com.whatever.caro.nickname.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.util.Locale

class WordDictionaryTest :
    DescribeSpec({

        val wordDictionary = WordDictionary()
        val englishRegex = "^[A-Za-z]+$".toRegex()
        val koreanRegex = "^[가-힣]+$".toRegex()

        describe("randomWord") {
            it("영어 locale에서 비어있지 않은 단어를 반환한다") {
                val word = wordDictionary.randomWord(Locale.ENGLISH, "animals")

                word shouldMatch englishRegex
            }

            it("한국어 locale에서 비어있지 않은 단어를 반환한다") {
                val word = wordDictionary.randomWord(Locale.KOREAN, "animals")

                word shouldMatch koreanRegex
            }

            it("미지원 카테고리로 조회하면 IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    wordDictionary.randomWord(Locale.ENGLISH, "unknown_category")
                }
            }

            it("미지원 locale은 기본 locale(영어)로 폴백하여 단어를 반환한다") {
                val unknownCategory = Locale.CHINA

                // 폴백된 단어가 실제 영어 카테고리에서 나오는지 여러 번 샘플링하여 검증
                repeat(10) {
                    val word = wordDictionary.randomWord(unknownCategory, "adjectives")
                    word shouldMatch englishRegex
                }
            }
        }

        describe("availableCategories") {
            val expectedCategories = setOf("adjectives", "animals", "colors", "nouns", "persons", "pokemons", "superheroes")

            it("영어 locale의 7개 카테고리를 반환한다") {
                val categories = wordDictionary.availableCategories(Locale.ENGLISH)

                categories.size shouldBe 7
                categories shouldContainAll expectedCategories
            }

            it("한국어 locale의 7개 카테고리를 반환한다") {
                val categories = wordDictionary.availableCategories(Locale.KOREAN)

                categories.size shouldBe 7
                categories shouldContainAll expectedCategories
            }
        }
    })
