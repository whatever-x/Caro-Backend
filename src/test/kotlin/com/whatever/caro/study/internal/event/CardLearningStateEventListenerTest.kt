package com.whatever.caro.study.internal.event

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.shouldNotBeNull
import org.awaitility.Awaitility.await
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.concurrent.TimeUnit

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class CardLearningStateEventListenerTest(
    private val transactionTemplate: TransactionTemplate,
    private val publisher: ApplicationEventPublisher,
    private val cardLearningStateRepository: CardLearningStateRepository,
) : DescribeSpec({

    fun createCls(
        cardId: Long,
        userId: Long = 1L,
        deckId: Long = 1L,
        status: CardLearningStatus = CardLearningStatus.NEW,
        intervalDays: Int = 0,
        easeFactor: BigDecimal = BigDecimal("2.50"),
        repetitions: Int = 0,
        lapses: Int = 0,
        consecutiveAgainCount: Int = 0,
        lastReviewedAt: Instant? = null,
        nextReviewAt: Instant? = null,
    ): CardLearningState =
        cardLearningStateRepository.save(
            CardLearningState(
                cardId = cardId,
                deckId = deckId,
                userId = userId,
                status = status,
                intervalDays = intervalDays,
                easeFactor = easeFactor,
                repetitions = repetitions,
                lapses = lapses,
                consecutiveAgainCount = consecutiveAgainCount,
                lastReviewedAt = lastReviewedAt,
                nextReviewAt = nextReviewAt,
            ),
        )

    describe("onCardDeleted") {
        it("CardsDeletedEvent를 수신하면 LearningState를 softdelete한다") {
            val cardIds = (1L..10L).toSet()
            val learningStates = cardIds.map { cardId -> createCls(cardId) }
            transactionTemplate.execute {
                publisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = 1L,
                        deletedCount = 1,
                        userId = 1L,
                        deletedCardIds = cardIds,
                    ),
                )
            }

            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                cardLearningStateRepository.findAllById(learningStates.map { it.id }).forAll {
                    it.deletedAt.shouldNotBeNull()
                }
            }
        }
    }
})
