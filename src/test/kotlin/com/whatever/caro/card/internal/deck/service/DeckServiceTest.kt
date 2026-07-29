package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckDto
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

@CaroModuleTest(extraIncludes = ["common"])
class DeckServiceTest(
    private val deckService: DeckService,
    private val deckRepository: DeckRepository,
) : DescribeSpec({

    afterEach {
        deckRepository.deleteAllInBatch()
    }

    fun saveDeck(
        userId: Long,
        name: String = "테스트 덱",
        description: String = "설명",
    ): Deck = deckRepository.save(Deck(userId = userId, name = name, description = description))

    describe("createDeck") {
        it("덱이 DB에 저장된다") {
            val userId = 1L
            val dto = CreateDeckDto(name = "새 덱", description = "덱 설명")

            val result = deckService.createDeck(userId, dto)

            val saved = deckRepository.findById(result.id).get()
            saved.userId shouldBe userId
            saved.name shouldBe "새 덱"
            saved.description shouldBe "덱 설명"
        }
    }

    describe("deleteDeck") {
        it("삭제 요청이 성공하고 덱 id를 반환한다") {
            // JDBC outbox 방식으로 이벤트를 저장하므로 soft delete는 스케줄러 처리 후 반영됨
            // 실제 soft delete 로직은 DeckDeletedEventListenerUnitTest에서 검증
            val userId = 1L
            val deck = saveDeck(userId)

            val result = deckService.deleteDeck(userId, DeleteDeckDto(deck.id))

            result.id shouldBe deck.id
        }

        it("존재하지 않는 덱이면 DeckNotFoundException을 던진다") {
            shouldThrow<DeckNotFoundException> {
                deckService.deleteDeck(1L, DeleteDeckDto(999L))
            }
        }

        it("다른 유저의 덱이면 DeckForbiddenException을 던진다") {
            val deck = saveDeck(userId = 2L)

            shouldThrow<DeckForbiddenException> {
                deckService.deleteDeck(1L, DeleteDeckDto(deck.id))
            }
        }
    }

    describe("updateDeck") {
        it("덱 이름과 설명이 DB에 즉시 반영된다") {
            val userId = 1L
            val deck = saveDeck(userId, name = "기존 이름", description = "기존 설명")

            val result = deckService.updateDeck(
                userId,
                UpdateDeckDto(deckId = deck.id, name = "새 이름", description = "새 설명"),
            )

            result.name shouldBe "새 이름"
            result.description shouldBe "새 설명"
            val updated = deckRepository.findById(deck.id).get()
            updated.name shouldBe "새 이름"
            updated.description shouldBe "새 설명"
        }

        it("존재하지 않는 덱이면 DeckNotFoundException을 던진다") {
            shouldThrow<DeckNotFoundException> {
                deckService.updateDeck(1L, UpdateDeckDto(deckId = 999L, name = "이름", description = "설명"))
            }
        }

        it("다른 유저의 덱이면 DeckForbiddenException을 던진다") {
            val deck = saveDeck(userId = 2L)

            shouldThrow<DeckForbiddenException> {
                deckService.updateDeck(1L, UpdateDeckDto(deckId = deck.id, name = "이름", description = "설명"))
            }
        }
    }

    describe("getDeck") {
        it("덱을 반환한다") {
            val deck = saveDeck(userId = 1L, name = "내 덱")

            val result = deckService.getDeck(deck.id)

            result.id shouldBe deck.id
            result.name shouldBe "내 덱"
        }

        it("존재하지 않으면 DeckNotFoundException을 던진다") {
            shouldThrow<DeckNotFoundException> {
                deckService.getDeck(999L)
            }
        }
    }

    describe("getDecks") {
        it("유저의 덱만 반환한다") {
            val userId = 1L
            saveDeck(userId, name = "덱1")
            saveDeck(userId, name = "덱2")
            saveDeck(userId = 2L, name = "다른 유저 덱")

            val result = deckService.getDecks(userId)

            result.size shouldBe 2
            result.all { it.userId == userId }.shouldBeTrue()
        }

        it("덱이 없으면 빈 목록을 반환한다") {
            deckService.getDecks(1L).shouldBeEmpty()
        }
    }
})
