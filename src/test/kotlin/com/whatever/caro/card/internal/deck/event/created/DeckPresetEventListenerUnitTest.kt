package com.whatever.caro.card.internal.deck.event.created

import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckPreset
import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional

class DeckPresetEventListenerUnitTest :
    DescribeSpec({

        val deckRepository = mockk<DeckRepository>(relaxed = true)
        val deckPresetRepository = mockk<DeckPresetRepository>(relaxed = true)
        val listener = DeckPresetEventListener(deckRepository, deckPresetRepository)

        beforeEach {
            clearMocks(deckRepository, deckPresetRepository)
        }

        describe("onDeckCreated") {
            it("프리셋과 덱 모두 존재하면 덱에 프리셋을 할당한다") {
                val deck = Deck(userId = 1L, name = "테스트 덱", description = "설명")
                val preset = DeckPreset(name = "기본 프리셋")
                every { deckPresetRepository.findById(1L) } returns Optional.of(preset)
                every { deckRepository.findById(1L) } returns Optional.of(deck)

                listener.onDeckCreated(DeckCreatedEvent(deckId = 1L, userId = 1L))

                deck.deckPreset shouldBe preset
            }

            it("프리셋이 존재하지 않으면 덱을 조회하지 않고 종료한다") {
                every { deckPresetRepository.findById(any()) } returns Optional.empty()

                listener.onDeckCreated(DeckCreatedEvent(deckId = 1L, userId = 1L))

                verify(exactly = 0) { deckRepository.findById(any()) }
            }

            it("덱이 존재하지 않으면 프리셋을 할당하지 않고 종료한다") {
                val preset = DeckPreset(name = "기본 프리셋")
                every { deckPresetRepository.findById(1L) } returns Optional.of(preset)
                every { deckRepository.findById(999L) } returns Optional.empty()

                listener.onDeckCreated(DeckCreatedEvent(deckId = 999L, userId = 1L))

                verify(exactly = 1) { deckRepository.findById(999L) }
            }
        }
    })
