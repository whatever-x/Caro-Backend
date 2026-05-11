package com.whatever.caro.card.internal.deck.event.deleted

import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class DeckDeletedEventListenerUnitTest : DescribeSpec({

    val deckRepository = mockk<DeckRepository>(relaxed = true)
    val listener = DeckDeletedEventListener(deckRepository)

    fun createDeckWithId(id: Long, userId: Long): Deck {
        val deck = Deck(userId = userId, name = "테스트 덱", description = "설명")
        val idField = Deck::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(deck, id)
        return deck
    }

    describe("onDeckDeleted") {
        it("덱을 찾아 소프트 딜리트한다") {
            val deckId = 10L
            val deck = createDeckWithId(id = deckId, userId = 1L)
            deck.isDeleted.shouldBeFalse()
            every { deckRepository.findById(deckId) } returns Optional.of(deck)

            listener.onDeckDeleted(DeckDeletedEvent(deckId = deckId, userId = 1L))

            deck.isDeleted.shouldBeTrue()
            deck.deletedAt.shouldNotBeNull()
        }

        it("덱이 존재하지 않으면 아무 작업도 하지 않는다") {
            every { deckRepository.findById(any()) } returns Optional.empty()

            listener.onDeckDeleted(DeckDeletedEvent(deckId = 999L, userId = 1L))

            verify(exactly = 0) { deckRepository.save(any()) }
        }
    }
})
