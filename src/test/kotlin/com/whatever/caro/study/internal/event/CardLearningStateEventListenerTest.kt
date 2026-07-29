package com.whatever.caro.study.internal.event

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class CardLearningStateEventListenerTest(
    private val transactionTemplate: TransactionTemplate,
    private val publisher: ApplicationEventPublisher,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val studySessionRepository: StudySessionRepository,
    private val clock: Clock,
) : DescribeSpec({

    val kstZoneId = ZoneId.of("Asia/Seoul")

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
        lastReviewedDate: LocalDate? = null,
        nextReviewDate: LocalDate? = null,
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
                lastReviewedDate = lastReviewedDate,
                nextReviewDate = nextReviewDate,
            ),
        )

    fun createSession(
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        startedAt: Instant,
        newCardsGoal: Int,
        reviewCardsGoal: Int,
        newCardsStudied: Int = 0,
        reviewCardsStudied: Int = 0,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = 1L,
                deckId = 1L,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = startedAt,
                timezone = kstZoneId,
                dayCutoffHour = 4,
                deckPresetIdSnapshot = 1L,
                newCardsStudied = newCardsStudied,
                reviewCardsStudied = reviewCardsStudied,
                newCardsGoal = newCardsGoal,
                reviewCardsGoal = reviewCardsGoal,
            ),
        )

    afterTest {
        studySessionRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
    }

    describe("onCardDeleted") {
        it("CardsDeletedEvent를 수신하면 LearningState를 softdelete한다") {
            val cardIds = (1L..10L).toSet()
            val learningStates = cardIds.map { cardId -> createCls(cardId) }
            val deletedAt = Instant.now(clock)
            transactionTemplate.execute {
                publisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = 1L,
                        deletedCount = cardIds.size,
                        userId = 1L,
                        deletedCardIds = cardIds,
                        deletedAt = deletedAt,
                        clientTimezone = kstZoneId,
                    ),
                )
            }

            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                val deletedLearningStates = cardLearningStateRepository.findAllById(learningStates.map { it.id })
                deletedLearningStates.forAll {
                    it.deletedAt shouldBe deletedAt
                }
                deletedLearningStates.map { it.cardId } shouldContainExactlyInAnyOrder cardIds
            }
        }

        it("카드 삭제 이벤트를 받으면 CLS softDelete와 세션 goal 재계산이 함께 일어난다") {
            val now = Instant.now(clock)
            val session = createSession(
                startedAt = now,
                newCardsGoal = 0,
                reviewCardsGoal = 5,
                reviewCardsStudied = 2,
            )
            val clsList = (0L..2L).map { i ->
                createCls(
                    cardId = i,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = session.sessionDate,
                )
            }
            studySessionRepository.findByIdOrNull(session.id)!!.status shouldBe StudySessionStatus.ACTIVE

            transactionTemplate.execute {
                publisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = 1L,
                        deletedCount = 3,
                        userId = 1L,
                        deletedCardIds = clsList.map { it.cardId }.toSet(), // 모든 cls 삭제
                        deletedAt = now,
                        clientTimezone = kstZoneId,
                    ),
                )
            }

            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                clsList.map { it.id }.let { ids ->
                    cardLearningStateRepository.findAllById(ids).forAll {
                        it.deletedAt shouldBe now
                    }
                }
                // goal이 5에서 감소 (정확한 수치와 COMPLETED 상태 변경 규칙은 엔티티/서비스 계층 테스트에서 진행)
                studySessionRepository.findByIdOrNull(session.id)!!.reviewCardsGoal shouldBeLessThan 5
            }
        }

        it("삭제된 카드에 대한 학습상태가 없으면 아무 변화가 없어야한다") {
            val now = Instant.now(clock)
            val initialReviewCardsGoal = 5
            val session = createSession(
                startedAt = now,
                newCardsGoal = 0,
                reviewCardsGoal = initialReviewCardsGoal,
                reviewCardsStudied = 2,
            )
            // 내 ACTIVE 오늘 세션이 있으므로, adjustGoals까지 진행됐다면 goal이 min(5, 2+0)=2로 감소해야함

            transactionTemplate.execute {
                publisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = 1L,
                        deletedCount = 1,
                        userId = 1L,
                        deletedCardIds = setOf(999L),
                        deletedAt = now,
                        clientTimezone = kstZoneId,
                    ),
                )
            }

            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                studySessionRepository.findByIdOrNull(session.id)!!.let {
                    it.status shouldBe StudySessionStatus.ACTIVE
                    it.reviewCardsGoal shouldBe initialReviewCardsGoal
                }
            }
        }

        it("같은 삭제 이벤트가 중복 전달되어도 첫 softDelete의 deletedAt이 유지된다") {
            val firstDeletedAt = Instant.now(clock)
            val cls = createCls(cardId = 200L)
            val event = CardsDeletedEvent(
                deckId = 1L,
                deletedCount = 1,
                userId = 1L,
                deletedCardIds = setOf(cls.cardId),
                deletedAt = firstDeletedAt,
                clientTimezone = kstZoneId,
            )

            transactionTemplate.execute {
                publisher.publishEvent(event)
            }
            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                val deletedCls = cardLearningStateRepository.findByIdOrNull(cls.id)!!
                deletedCls.deletedAt shouldBe firstDeletedAt
            }

            val secondDeletedAt = firstDeletedAt.plusSeconds(60)
            transactionTemplate.execute {
                publisher.publishEvent(event.copy(deletedAt = secondDeletedAt))
            }
            await().atMost(2, TimeUnit.SECONDS).untilAsserted {
                val deletedCls = cardLearningStateRepository.findByIdOrNull(cls.id)!!
                deletedCls.deletedAt shouldBe firstDeletedAt
            }
        }

        it("발행 트랜잭션이 롤백되면 리스너가 실행되지 않는다") {
            val now = Instant.now(clock)
            val cls = createCls(cardId = 300L)

            transactionTemplate.execute { status ->
                publisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = 1L,
                        deletedCount = 1,
                        userId = 1L,
                        deletedCardIds = setOf(cls.cardId),
                        deletedAt = now,
                        clientTimezone = kstZoneId,
                    ),
                )
                status.setRollbackOnly()
            }

            val savedCls = cardLearningStateRepository.findByIdOrNull(cls.id)!!
            savedCls.deletedAt shouldBe null
        }
    }
})
