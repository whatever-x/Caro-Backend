package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckDto
import com.whatever.caro.card.internal.deck.event.created.DeckCreatedEvent
import com.whatever.caro.card.internal.deck.event.deleted.DeckDeletedEvent
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import java.util.Optional

class DeckServiceUnitTest : DescribeSpec({

    val deckRepository = mockk<DeckRepository>()
    val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val deckService = DeckService(deckRepository, eventPublisher)

    fun createDeckWithId(
        id: Long,
        userId: Long,
        name: String = "테스트 덱",
        description: String = "설명",
    ): Deck {
        val deck = Deck(userId = userId, name = name, description = description)
        val idField = Deck::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(deck, id)
        return deck
    }

    describe("createDeck") {
        it("덱을 저장하고 DeckCreatedEvent를 발행한다") {
            val userId = 1L
            val dto = CreateDeckDto(name = "테스트 덱", description = "설명")
            val savedDeck = createDeckWithId(id = 10L, userId = userId, name = "테스트 덱", description = "설명")
            every { deckRepository.save(any()) } returns savedDeck

            val result = deckService.createDeck(userId, dto)

            result.id shouldBe 10L
            result.name shouldBe "테스트 덱"
            result.description shouldBe "설명"
            verify { eventPublisher.publishEvent(DeckCreatedEvent(deckId = 10L, userId = userId)) }
        }
    }

    describe("deleteDeck") {
        it("소유자가 삭제 요청 시 DeckDeletedEvent를 발행하고 덱 id를 반환한다") {
            val userId = 1L
            val deckId = 10L
            val deck = createDeckWithId(id = deckId, userId = userId)
            every { deckRepository.findById(deckId) } returns Optional.of(deck)

            val result = deckService.deleteDeck(userId, DeleteDeckDto(deckId))

            result.id shouldBe deckId
            verify { eventPublisher.publishEvent(DeckDeletedEvent(deckId = deckId, userId = userId)) }
        }

        it("존재하지 않는 덱이면 DeckNotFoundException을 던진다") {
            every { deckRepository.findById(any()) } returns Optional.empty()

            shouldThrow<DeckNotFoundException> {
                deckService.deleteDeck(1L, DeleteDeckDto(999L))
            }
        }

        it("다른 유저의 덱이면 DeckForbiddenException을 던진다") {
            val deck = createDeckWithId(id = 10L, userId = 2L)
            every { deckRepository.findById(10L) } returns Optional.of(deck)

            shouldThrow<DeckForbiddenException> {
                deckService.deleteDeck(1L, DeleteDeckDto(10L))
            }
        }
    }

    describe("updateDeck") {
        it("덱 이름과 설명을 수정하고 반환한다") {
            val userId = 1L
            val deck = createDeckWithId(id = 10L, userId = userId, name = "기존 이름", description = "기존 설명")
            every { deckRepository.findById(10L) } returns Optional.of(deck)

            val result = deckService.updateDeck(
                userId,
                UpdateDeckDto(deckId = 10L, name = "새 이름", description = "새 설명"),
            )

            result.id shouldBe 10L
            result.name shouldBe "새 이름"
            result.description shouldBe "새 설명"
        }

        it("존재하지 않는 덱이면 DeckNotFoundException을 던진다") {
            every { deckRepository.findById(any()) } returns Optional.empty()

            shouldThrow<DeckNotFoundException> {
                deckService.updateDeck(1L, UpdateDeckDto(deckId = 999L, name = "이름", description = "설명"))
            }
        }

        it("다른 유저의 덱이면 DeckForbiddenException을 던진다") {
            val deck = createDeckWithId(id = 10L, userId = 2L)
            every { deckRepository.findById(10L) } returns Optional.of(deck)

            shouldThrow<DeckForbiddenException> {
                deckService.updateDeck(1L, UpdateDeckDto(deckId = 10L, name = "이름", description = "설명"))
            }
        }
    }

    describe("getDeck") {
        it("덱을 반환한다") {
            val deck = createDeckWithId(id = 10L, userId = 1L, name = "내 덱")
            every { deckRepository.findById(10L) } returns Optional.of(deck)

            val result = deckService.getDeck(10L)

            result.id shouldBe 10L
            result.name shouldBe "내 덱"
        }

        it("존재하지 않으면 DeckNotFoundException을 던진다") {
            every { deckRepository.findById(any()) } returns Optional.empty()

            shouldThrow<DeckNotFoundException> {
                deckService.getDeck(999L)
            }
        }
    }

    describe("getDecks") {
        it("유저의 덱 목록을 반환한다") {
            val userId = 1L
            val decks = listOf(
                createDeckWithId(id = 1L, userId = userId),
                createDeckWithId(id = 2L, userId = userId),
            )
            every { deckRepository.findByUserId(userId) } returns decks

            val result = deckService.getDecks(userId)

            result.size shouldBe 2
        }

        it("덱이 없으면 빈 목록을 반환한다") {
            every { deckRepository.findByUserId(any()) } returns emptyList()

            deckService.getDecks(1L).shouldBeEmpty()
        }
    }
})
