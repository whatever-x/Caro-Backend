package com.whatever.caro.card.internal.card

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.service.DeckService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@ActiveProfiles("library-service-test")
class CardDeletionConcurrencyIntegrationTest(
    cards: CardService,
    decks: DeckService,
    jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
) : DescribeSpec({
    val userId = 8291L
    val timezone = ZoneId.of("UTC")

    fun createDeckWithCards(
        count: Int = 2,
    ): Pair<Long, List<Long>> {
        val deckId = decks.createDeck(userId, CreateDeckDto("동시 삭제 검증", "회귀 테스트")).id
        val items = List(count) { index ->
            CreateCardItemDto(1L, mapOf("front" to "front $index", "back" to "back $index"))
        }
        val created = cards.createCards(userId, CreateCardsDto(deckId, items))
        return deckId to created.items.map { it.cardId }
    }

    fun assertCount(
        deckId: Long,
        expected: Long,
    ) {
        val actual = jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE deck_id = ? AND deleted_at IS NULL",
            Long::class.java,
            deckId,
        )
        val cached = jdbc.queryForObject("SELECT card_count FROM decks WHERE id = ?", Long::class.java, deckId)
        actual shouldBe expected
        cached shouldBe expected
    }

    fun deleteConcurrently(
        requests: List<Set<Long>>,
    ) {
        val barrier = CyclicBarrier(requests.size)
        val pool = Executors.newFixedThreadPool(requests.size)
        try {
            val futures = requests.map { cardIds ->
                pool.submit {
                    TransactionTemplate(transactionManager).executeWithoutResult {
                        // Both transactions see the cards before either deletion commits.
                        jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE user_id = ?", Long::class.java, userId)
                        barrier.await(15, TimeUnit.SECONDS)
                        cards.deleteCard(userId, timezone, DeleteCardDto(cardIds))
                    }
                }
            }
            for (future in futures) {
                future.get(30, TimeUnit.SECONDS)
            }
        } finally {
            pool.shutdownNow()
        }
    }

    afterEach {
        jdbc.update("DELETE FROM cards WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM notes WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM decks WHERE user_id = ?", userId)
    }

    it("같은 카드의 동시 삭제는 카드 수를 한 번만 감소시킨다") {
        val (deckId, cardIds) = createDeckWithCards()
        val request = setOf(cardIds.first())

        deleteConcurrently(listOf(request, request))

        assertCount(deckId, 1)
    }

    it("같은 노트의 서로 다른 카드를 동시에 삭제하면 마지막 카드와 함께 노트도 삭제한다") {
        val (deckId, cardIds) = createDeckWithCards()
        val noteId = jdbc.queryForObject("SELECT note_id FROM cards WHERE id = ?", Long::class.java, cardIds.first())!!
        jdbc.update("UPDATE cards SET note_id = ? WHERE id = ?", noteId, cardIds.last())

        deleteConcurrently(cardIds.map { setOf(it) })

        assertCount(deckId, 0)
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notes WHERE id = ? AND deleted_at IS NULL",
            Long::class.java,
            noteId,
        ) shouldBe 0
    }

    it("여러 덱의 같은 카드를 서로 반대 순서로 삭제해도 중복 차감이나 deadlock이 없다") {
        val (firstDeckId, firstCards) = createDeckWithCards()
        val (secondDeckId, secondCards) = createDeckWithCards()
        val first = firstCards.first()
        val second = secondCards.first()

        deleteConcurrently(listOf(linkedSetOf(first, second), linkedSetOf(second, first)))

        assertCount(firstDeckId, 1)
        assertCount(secondDeckId, 1)
    }

    it("여러 덱의 카드 삭제 후 실패하면 카드와 노트 및 덱별 카드 수를 함께 rollback한다") {
        val (firstDeckId, firstCards) = createDeckWithCards()
        val (secondDeckId, secondCards) = createDeckWithCards()
        val first = firstCards.first()
        val second = secondCards.first()
        val firstNote = jdbc.queryForObject("SELECT note_id FROM cards WHERE id = ?", Long::class.java, first)!!
        val secondNote = jdbc.queryForObject("SELECT note_id FROM cards WHERE id = ?", Long::class.java, second)!!

        shouldThrow<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                cards.deleteCard(userId, timezone, DeleteCardDto(setOf(first, second)))
                error("failure after card deletion")
            }
        }

        assertCount(firstDeckId, 2)
        assertCount(secondDeckId, 2)
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM notes WHERE id IN (?, ?) AND deleted_at IS NULL",
            Long::class.java,
            firstNote,
            secondNote,
        ) shouldBe 2L
    }

    it("같은 덱의 카드 추가와 삭제가 겹쳐도 실제 카드 수와 합계가 일치한다") {
        val (deckId, cardIds) = createDeckWithCards(199)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val create = pool.submit {
                start.await()
                cards.createCards(
                    userId,
                    CreateCardsDto(deckId, listOf(CreateCardItemDto(1L, mapOf("front" to "new", "back" to "new")))),
                )
            }
            val delete = pool.submit {
                start.await()
                cards.deleteCard(userId, timezone, DeleteCardDto(setOf(cardIds.first())))
            }
            start.countDown()
            create.get(30, TimeUnit.SECONDS)
            delete.get(30, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }

        assertCount(deckId, 199)
    }
})
