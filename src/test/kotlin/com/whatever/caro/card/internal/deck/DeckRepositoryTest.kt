package com.whatever.caro.card.internal.deck

import com.whatever.caro.CaroModuleTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate

@CaroModuleTest(extraIncludes = ["common"])
class DeckRepositoryTest(
    private val deckRepository: DeckRepository,
    transactionManager: PlatformTransactionManager,
) : DescribeSpec({
    val tx = TransactionTemplate(transactionManager)

    afterTest { deckRepository.deleteAllInBatch() }

    fun saveDeck(
        cardCount: Int,
    ): Deck = deckRepository.save(Deck(userId = 1L, name = "덱", description = "설명", cardCount = cardCount))

    describe("decreaseCardCount") {
        it("카드 개수를 감소시키고 수정된 행 수를 반환한다") {
            // given
            val deck = saveDeck(cardCount = 10)

            // when
            val updated = tx.execute {
                deckRepository.decreaseCardCount(deckId = deck.id, amount = 3)
            }

            // then
            updated shouldBe 1
            deckRepository.findById(deck.id).get().cardCount shouldBe 7
        }

        it("존재하지 않는 덱이면 수정된 행 수는 0이다") {
            // when
            val updated = tx.execute {
                deckRepository.decreaseCardCount(deckId = Long.MAX_VALUE, amount = 3)
            }

            // then
            updated shouldBe 0
        }
    }

    describe("lockAllByIdIn") {
        it("존재하는 대상 덱을 id 오름차순으로 한 번에 조회한다") {
            val first = saveDeck(cardCount = 1)
            val second = saveDeck(cardCount = 2)
            val third = saveDeck(cardCount = 3)

            val lockedIds = tx.execute {
                deckRepository.lockAllByIdIn(listOf(third.id, Long.MAX_VALUE, first.id, second.id)).map { it.id }
            }

            lockedIds shouldContainExactly listOf(first.id, second.id, third.id)
        }

        it("대상 id가 비어 있으면 빈 목록을 반환한다") {
            tx.execute { deckRepository.lockAllByIdIn(emptyList()) } shouldBe emptyList()
        }
    }

    describe("lockByIdAndDeletedAtIsNull") {
        it("잠근 덱의 카드 수 변경을 commit하면 저장한다") {
            val deck = saveDeck(cardCount = 5)

            tx.executeWithoutResult {
                val locked = requireNotNull(deckRepository.lockByIdAndDeletedAtIsNull(deck.id))
                locked.cardCount += 2
            }

            deckRepository.findById(deck.id).get().cardCount shouldBe 7
        }

        it("존재하지 않는 덱이면 null을 반환한다") {
            tx.execute { deckRepository.lockByIdAndDeletedAtIsNull(Long.MAX_VALUE) } shouldBe null
        }
    }
})
