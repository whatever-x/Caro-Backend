package com.whatever.caro.study.internal.event

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.api.event.CardChunkCreatedEvent
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.queryForList
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.util.ClassUtils
import java.time.Clock
import java.time.Instant
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class CardChunkLearningStateEventListenerTest(
    private val transactionTemplate: TransactionTemplate,
    private val publisher: ApplicationEventPublisher,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : DescribeSpec({

    val userId = 8_001L

    val listenerId = CardChunkLearningStateEventListener::class.java.declaredMethods
        .single { it.parameterTypes.contentEquals(arrayOf(CardChunkCreatedEvent::class.java)) }
        .let { method -> ClassUtils.getQualifiedMethodName(method) + "(${CardChunkCreatedEvent::class.java.name})" }

    fun publish(
        event: CardChunkCreatedEvent,
    ) {
        transactionTemplate.execute { publisher.publishEvent(event) }
    }

    fun statesOf(
        cardIds: List<Long>,
    ): List<CardLearningState> = cardLearningStateRepository.findAllByUserIdAndCardIdIn(userId = userId, cardIds = cardIds)

    fun cardIdsInIdOrder(): List<Long> =
        jdbcTemplate.queryForList<Long>("SELECT card_id FROM card_learning_states WHERE user_id = ? ORDER BY id", userId)
            .filterNotNull()

    // 리스너 실행이 끝난 발행만 archive로 옮겨지므로, 결과 단언 전에 처리 완료를 기다리는 기준으로 쓴다.
    fun publicationListenerIds(
        deckId: Long,
        table: String,
    ): List<String> =
        jdbcTemplate.queryForList<String>(
            "SELECT listener_id FROM $table WHERE event_type = ? AND JSON_EXTRACT(serialized_event, '$.deckId') = ?",
            CardChunkCreatedEvent::class.java.name,
            deckId,
        ).filterNotNull()

    fun archivedListenerIds(
        deckId: Long,
    ): List<String> = publicationListenerIds(deckId, "event_publication_archive")

    beforeTest {
        cardLearningStateRepository.deleteAllInBatch()
    }

    afterTest {
        cardLearningStateRepository.deleteAllInBatch()
    }

    describe("onCardChunkCreated") {
        it("CardChunkCreatedEvent를 받으면 cardId마다 NEW 학습 상태를 만든다") {
            val cardIds = listOf(11L, 12L, 13L)

            publish(CardChunkCreatedEvent(cardIds = cardIds, deckId = 9_301L, userId = userId))

            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                val states = statesOf(cardIds)
                states.map { it.cardId } shouldContainExactlyInAnyOrder cardIds
                states.forAll {
                    it.status shouldBe CardLearningStatus.NEW
                    it.deckId shouldBe 9_301L
                }
            }
        }

        it("cardId 1,000개 이벤트는 학습 상태 1,000개를 이벤트 순서의 id로 만든다") {
            val cardIds = (1..1000).map { 60_000L - it }

            publish(CardChunkCreatedEvent(cardIds = cardIds, deckId = 9_302L, userId = userId))

            await().atMost(10, TimeUnit.SECONDS).untilAsserted {
                cardIdsInIdOrder() shouldBe cardIds
            }
        }

        it("같은 이벤트가 두 번 들어와도 cardId 개수만큼만 생성된다") {
            val cardIds = listOf(21L, 22L)
            val event = CardChunkCreatedEvent(cardIds = cardIds, deckId = 9_303L, userId = userId)
            publish(event)
            await().atMost(5, TimeUnit.SECONDS).untilAsserted { archivedListenerIds(9_303L) shouldHaveSize 1 }
            val idsAfterFirst = statesOf(cardIds).map { it.id }.toSet()

            publish(event)

            await().atMost(5, TimeUnit.SECONDS).untilAsserted { archivedListenerIds(9_303L) shouldHaveSize 2 }
            statesOf(cardIds).map { it.id }.toSet() shouldBe idsAfterFirst
            idsAfterFirst shouldHaveSize 2
        }

        it("이미 학습 상태가 있는 cardId는 제외하고 생성한다") {
            val existing = cardLearningStateRepository.save(CardLearningState(cardId = 31L, deckId = 9_304L, userId = userId))

            publish(CardChunkCreatedEvent(cardIds = listOf(31L, 32L), deckId = 9_304L, userId = userId))

            await().atMost(5, TimeUnit.SECONDS).untilAsserted { archivedListenerIds(9_304L) shouldHaveSize 1 }
            val states = statesOf(listOf(31L, 32L))
            states.map { it.cardId } shouldContainExactlyInAnyOrder listOf(31L, 32L)
            states.single { it.cardId == 31L }.id shouldBe existing.id
        }

        it("cardId의 학습 상태가 soft delete된 상태여도 유니크 위반 없이 나머지만 생성한다") {
            val deleted = CardLearningState(cardId = 41L, deckId = 9_305L, userId = userId)
            deleted.softDelete(Instant.now(clock))
            cardLearningStateRepository.save(deleted)

            publish(CardChunkCreatedEvent(cardIds = listOf(41L, 42L), deckId = 9_305L, userId = userId))

            await().atMost(5, TimeUnit.SECONDS).untilAsserted { archivedListenerIds(9_305L) shouldHaveSize 1 }
            cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(userId = userId, cardIds = listOf(41L, 42L))
                .map { it.cardId } shouldBe listOf(42L)
        }

        it("직렬화 값이 4,000자를 넘는 CardChunkCreatedEvent도 처리되고 발행 기록이 archive 테이블로 옮겨진다") {
            // event_publication의 SERIALIZED_EVENT 기본 상한은 4,000자이며, 8자리 cardId 600개면 직렬화 값이 그보다 길다.
            val manyLongCardIds = (10_000_000L until 10_000_600L).toList()

            publish(CardChunkCreatedEvent(cardIds = manyLongCardIds, deckId = 9_306L, userId = userId))

            await().atMost(10, TimeUnit.SECONDS).untilAsserted {
                val lengths = jdbcTemplate.queryForList<Long>(
                    "SELECT LENGTH(serialized_event) FROM event_publication_archive WHERE listener_id = ? AND JSON_EXTRACT(serialized_event, '$.deckId') = ?",
                    listenerId,
                    9_306L,
                ).filterNotNull()
                lengths shouldHaveSize 1
                lengths.single() shouldBeGreaterThan 4000L
            }
            statesOf(manyLongCardIds) shouldHaveSize manyLongCardIds.size
        }

        it("발행 트랜잭션이 롤백되면 리스너가 실행되지 않는다") {
            transactionTemplate.execute { status ->
                publisher.publishEvent(CardChunkCreatedEvent(cardIds = listOf(51L), deckId = 9_307L, userId = userId))
                status.setRollbackOnly()
            }
            publish(CardChunkCreatedEvent(cardIds = listOf(52L), deckId = 9_308L, userId = userId))

            await().atMost(5, TimeUnit.SECONDS).untilAsserted { statesOf(listOf(52L)) shouldHaveSize 1 }
            statesOf(listOf(51L)).shouldBeEmpty()
            publicationListenerIds(9_307L, "event_publication").shouldBeEmpty()
            archivedListenerIds(9_307L).shouldBeEmpty()
        }

        it("CardChunkCreatedEvent의 발행 기록은 이 리스너에만 남고 학습 상태가 정확히 cardId 개수만큼 생긴다") {
            val cardIds = (61L..70L).toList()

            publish(CardChunkCreatedEvent(cardIds = cardIds, deckId = 9_309L, userId = userId))

            await().atMost(5, TimeUnit.SECONDS).untilAsserted { archivedListenerIds(9_309L) shouldBe listOf(listenerId) }
            publicationListenerIds(9_309L, "event_publication").shouldBeEmpty()
            statesOf(cardIds) shouldHaveSize cardIds.size
        }
    }
})
