package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.transaction.IllegalTransactionStateException
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class CardLearningStateBulkRepositoryTest(
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : DescribeSpec({

    val userId = 8_101L
    val deckId = 8_201L

    fun newState(
        cardId: Long,
    ) = CardLearningState(cardId = cardId, deckId = deckId, userId = userId)

    fun reviewedState(
        cardId: Long,
    ) = CardLearningState(
        cardId = cardId,
        deckId = deckId,
        userId = userId,
        status = CardLearningStatus.REVIEW,
        previousStatus = CardLearningStatus.NEW,
        intervalDays = 6,
        repetitions = 2,
        easeFactor = BigDecimal("2.36"),
        lapses = 1,
        nextReviewDate = LocalDate.parse("2026-10-21"),
        lastReviewedDate = LocalDate.parse("2026-10-15"),
        consecutiveAgainCount = 1,
        totalReviews = 3,
    )

    // 컬럼 목록을 고정하지 않아야 나중에 추가된 컬럼도 JPA 저장값과 비교된다.
    fun storedColumns(
        cardId: Long,
    ): Map<String, Any?> =
        jdbcTemplate.queryForMap("SELECT * FROM card_learning_states WHERE card_id = ?", cardId) -
            setOf("id", "card_id", "created_at", "updated_at")

    fun comInsert(): Long = jdbcTemplate.queryForObject("SHOW SESSION STATUS LIKE 'Com_insert'") { rs, _ -> rs.getLong("Value") }!!

    beforeTest {
        cardLearningStateRepository.deleteAllInBatch()
    }

    afterTest {
        cardLearningStateRepository.deleteAllInBatch()
    }

    describe("insertAll") {
        it("JPA로 저장한 학습 상태와 id, card_id, 시각을 뺀 모든 컬럼이 같고 시각은 now다") {
            val now = Instant.now(clock)
            cardLearningStateRepository.saveAll(listOf(newState(1L), reviewedState(3L)))

            transactionTemplate.execute { cardLearningStateRepository.insertAll(listOf(newState(2L), reviewedState(4L)), now) }

            storedColumns(2L) shouldBe storedColumns(1L)
            storedColumns(4L) shouldBe storedColumns(3L)
            val inserted = cardLearningStateRepository.findAllByUserIdAndCardIdIn(userId = userId, cardIds = listOf(2L)).single()
            inserted.createdAt shouldBe now
            inserted.updatedAt shouldBe now
        }

        it("1,000개는 INSERT 한 문장으로 저장되고 id 순서가 입력 cardId 순서와 같다") {
            val cardIds = (1..1000).map { 50_000L - it }

            val inserts = transactionTemplate.execute {
                val before = comInsert()
                cardLearningStateRepository.insertAll(cardIds.map { newState(it) }, Instant.now(clock))
                comInsert() - before
            }

            inserts shouldBe 1L
            jdbcTemplate.queryForList<Long>("SELECT card_id FROM card_learning_states WHERE user_id = ? ORDER BY id", userId) shouldBe
                cardIds
        }

        it("트랜잭션 밖에서 부르면 IllegalTransactionStateException을 던지고 저장하지 않는다") {
            shouldThrow<IllegalTransactionStateException> {
                cardLearningStateRepository.insertAll(listOf(newState(1L)), Instant.now(clock))
            }

            cardLearningStateRepository.count() shouldBe 0L
        }
    }
})
