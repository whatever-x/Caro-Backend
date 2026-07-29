package com.whatever.caro.card.internal.deck

import com.whatever.caro.CaroModuleTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.transaction.annotation.Transactional

@CaroModuleTest(extraIncludes = ["common"])
@Transactional
class DeckRepositoryTest(
    private val deckRepository: DeckRepository,
) : DescribeSpec({

    afterTest { deckRepository.deleteAllInBatch() }

    fun saveDeck(
        cardCount: Int,
    ): Deck = deckRepository.save(Deck(userId = 1L, name = "덱", description = "설명", cardCount = cardCount))

    describe("decreaseCardCount") {
        it("카드 개수를 감소시키고 수정된 행 수를 반환한다") {
            // given
            val deck = saveDeck(cardCount = 10)

            // when
            val updated = deckRepository.decreaseCardCount(
                deckId = deck.id,
                amount = 3,
            )

            // then
            updated shouldBe 1
            deckRepository.findById(deck.id).get().cardCount shouldBe 7
        }

        it("존재하지 않는 덱이면 수정된 행 수는 0이다") {
            // when
            val updated = deckRepository.decreaseCardCount(
                deckId = Long.MAX_VALUE,
                amount = 3,
            )

            // then
            updated shouldBe 0
        }
    }

    describe("increaseCardCount") {
        it("카드 개수를 증가시키고 수정된 행 수를 반환한다") {
            val deck = saveDeck(cardCount = 5)

            val updated = deckRepository.increaseCardCount(deck.id, 2)

            updated shouldBe 1
            deckRepository.findById(deck.id).get().cardCount shouldBe 7
        }

        it("존재하지 않는 덱이면 수정된 행 수는 0이다") {
            val updated = deckRepository.increaseCardCount(Long.MAX_VALUE, 2)

            updated shouldBe 0
        }
    }
})
