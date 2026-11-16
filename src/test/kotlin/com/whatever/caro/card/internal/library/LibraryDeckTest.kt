package com.whatever.caro.card.internal.library

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

class LibraryDeckTest :
    DescribeSpec({
        fun cards(
            count: Int,
        ): List<LibraryCard> = List(count) { LibraryCard(1L, "앞면", "뒷면", it) }

        it("이름 50자와 설명 500자 및 카드 200장을 모두 복사할 수 있다") {
            val deck = LibraryDeck("가".repeat(50), "나".repeat(500))

            deck.isValidForCopy(cards(200)) shouldBe true
            deck.isValidForCopy(cards(1)) shouldBe true
        }

        it("이름이나 설명이 공백이거나 길이 제한을 넘으면 복사하지 않는다") {
            listOf(
                LibraryDeck(" ", "설명"),
                LibraryDeck("가".repeat(51), "설명"),
                LibraryDeck("이름", "\n"),
                LibraryDeck("이름", "나".repeat(501)),
            ).forEach { it.isValidForCopy(cards(1)) shouldBe false }
        }

        it("카드가 없거나 200장을 넘으면 복사하지 않는다") {
            val deck = LibraryDeck("이름", "설명")

            deck.isValidForCopy(emptyList()) shouldBe false
            deck.isValidForCopy(cards(201)) shouldBe false
        }

        it("카드 앞면이나 뒷면이 공백이면 일부만 복사하지 않는다") {
            val deck = LibraryDeck("이름", "설명")

            deck.isValidForCopy(cards(1) + LibraryCard(1L, " ", "뒷면", 1)) shouldBe false
            deck.isValidForCopy(cards(1) + LibraryCard(1L, "앞면", "\n", 1)) shouldBe false
        }
    })
