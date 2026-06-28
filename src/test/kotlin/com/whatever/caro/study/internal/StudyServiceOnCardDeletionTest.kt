package com.whatever.caro.study.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.test.ApplicationModuleTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class StudyServiceOnCardDeletionTest(
    private val studyService: StudyService,
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
) : DescribeSpec({

    val kstZoneId = ZoneId.of("Asia/Seoul")
    val dayCutoffHour = 4
    val baseNow: Instant = LocalDateTime.parse("2026-06-03T10:00:00").atZone(kstZoneId).toInstant()
    val yesterday: Instant = baseNow.minus(1L, ChronoUnit.DAYS)

    afterTest {
        studySessionRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
    }

    fun createSession(
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        startedAt: Instant = baseNow,
        newCardsGoal: Int = 10,
        reviewCardsGoal: Int = 10,
        newCardsStudied: Int = 0,
        reviewCardsStudied: Int = 0,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = userId,
                deckId = deckId,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = startedAt,
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
                deckPresetIdSnapshot = 1L,
                newCardsStudied = newCardsStudied,
                reviewCardsStudied = reviewCardsStudied,
                newCardsGoal = newCardsGoal,
                reviewCardsGoal = reviewCardsGoal,
                endedAt = baseNow.takeIf { status == StudySessionStatus.COMPLETED },
            ),
        )

    fun createCls(
        cardId: Long,
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
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

    describe("adjustGoalsOnCardDeletion") {
        context("ACTIVE 상태인 오늘 세션이 아니면 아무것도 변경하지 않는다") {

            it("COMPLETED 세션이면 상태를 변경하지 않는다") {
                val session = createSession(
                    status = StudySessionStatus.COMPLETED,
                    newCardsGoal = 5,
                    newCardsStudied = 0,
                    reviewCardsGoal = 5,
                    reviewCardsStudied = 0,
                )
                // CLS 0장 → ACTIVE였다면 recalculateGoals(0,0)로 goal이 5,5 → 0,0까지 떨어질 조건
                // 하지만 COMPLETED이므로 early-return되어 5,5 그대로여야 한다

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                persisted.newCardsGoal shouldBe 5
                persisted.reviewCardsGoal shouldBe 5
                persisted.status shouldBe StudySessionStatus.COMPLETED
            }

            it("STOPPED 세션이면 상태를 변경하지 않는다") {
                val session = createSession(
                    status = StudySessionStatus.STOPPED,
                    newCardsGoal = 5,
                    newCardsStudied = 0,
                    reviewCardsGoal = 5,
                    reviewCardsStudied = 0,
                )
                // CLS 0장 → ACTIVE였다면 recalculateGoals(0,0)로 goal이 5,5 → 0,0까지 떨어질 조건
                // 하지만 STOPPED이므로 early-return되어 5,5 그대로여야 한다

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                persisted.status shouldBe StudySessionStatus.STOPPED
                persisted.newCardsGoal shouldBe 5
                persisted.reviewCardsGoal shouldBe 5
            }

            it("오늘이 아닌 ACTIVE 세션이면 상태를 변경하지 않는다") {
                val session = createSession(
                    status = StudySessionStatus.ACTIVE,
                    startedAt = yesterday,
                    newCardsGoal = 5,
                    reviewCardsGoal = 5,
                )

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                persisted.newCardsGoal shouldBe 5
                persisted.reviewCardsGoal shouldBe 5
                persisted.startedAt shouldBe yesterday
            }

            it("세션이 없으면 아무 일도 발생하지 않는다") {
                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                studySessionRepository.findAll() shouldBe emptyList()
            }
        }

        context("adjustGoalsOnCardDeletion의 available 카드 계산") {

            it("오늘(sessionDate) 평가된 NEW 카드는 availableNew에서 제외된다") {
                val session = createSession(
                    newCardsGoal = 5,
                    newCardsStudied = 2,
                )
                // 처음 학습하는 카드와 어제 학습했던 카드이기 때문에 availableNew에 포함되는 카드 2장
                createCls(cardId = 1L, status = CardLearningStatus.NEW, lastReviewedDate = null)
                createCls(cardId = 2L, status = CardLearningStatus.NEW, lastReviewedDate = session.sessionDate.minusDays(1L))

                // availableNew에서 제외되는 카드 1장 (lastReviewedDate == sessionDate)
                createCls(
                    cardId = 3L,
                    status = CardLearningStatus.NEW,
                    lastReviewedDate = session.sessionDate,
                )

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // newGoal = min(5, 2+2) = 4
                persisted.newCardsGoal shouldBe 4
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }

            it("이미 softDelete된 CLS는 availableReview에서 제외된다") {
                val session = createSession(
                    reviewCardsGoal = 5,
                    reviewCardsStudied = 2,
                )
                // availableReview에 포함되는 REVIEW 4장 생성
                val reviewCards = (1..4).map { i ->
                    createCls(
                        i.toLong(),
                        status = CardLearningStatus.REVIEW,
                        nextReviewDate = session.sessionDate,
                    )
                }
                // 3장 softDelete, availableReview에 포함될 수 있는 카드는 1장
                reviewCards.take(3).forEach { cls ->
                    cls.softDelete(deletedAt = baseNow)
                }
                cardLearningStateRepository.saveAll(reviewCards)

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // reviewGoal = min(5, 2+1) = 3
                persisted.reviewCardsGoal shouldBe 3
            }

            it("이미 softDelete된 NEW 카드는 availableNew에서 제외된다") {
                val session = createSession(
                    newCardsGoal = 5,
                    newCardsStudied = 2,
                )
                // availableNew에 포함되는 NEW 카드 2장
                createCls(cardId = 1L, status = CardLearningStatus.NEW)
                createCls(cardId = 2L, status = CardLearningStatus.NEW)
                // softDelete된 NEW 카드 1장은 availableNew에서 제외
                createCls(cardId = 3L, status = CardLearningStatus.NEW).apply {
                    softDelete(deletedAt = baseNow)
                    cardLearningStateRepository.save(this)
                }

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // newGoal = min(5, 2+2) = 4
                persisted.newCardsGoal shouldBe 4
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }

            it("다음 복습일이 미래(sessionDate 다음날)인 REVIEW 카드는 availableReview에서 제외된다") {
                // card.nextReviewDate > sessionDate
                val s = createSession(
                    reviewCardsGoal = 5,
                    reviewCardsStudied = 0,
                )
                // 오늘 세션에 포함되어 availableReview에 포함되는 카드
                createCls(
                    30L,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = s.sessionDate,
                )
                // 다음 세션에 복습 대상이므로 포함 안되는 카드
                createCls(
                    31L,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = s.sessionDate.plus(1, ChronoUnit.DAYS),
                )

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(s.id)!!
                // reviewGoal = min(5, 0+1) = 1
                persisted.reviewCardsGoal shouldBe 1
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }

            it("오늘(sessionDate) 학습한 NEW 카드는 availableNew에서 제외된다") {
                // card.lastReviewedDate == sessionDate
                val session = createSession(
                    newCardsGoal = 5,
                    newCardsStudied = 0,
                )
                // 어제(sessionDate 이전) 학습한 NEW 카드는 availableNew에 포함
                createCls(
                    cardId = 1L,
                    status = CardLearningStatus.NEW,
                    lastReviewedDate = session.sessionDate.minusDays(1L),
                )
                // 오늘(sessionDate) 학습한 카드는 같은 날 학습이므로 제외
                createCls(
                    cardId = 2L,
                    status = CardLearningStatus.NEW,
                    lastReviewedDate = session.sessionDate,
                )

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // newGoal = min(5, 0+1) = 1
                persisted.newCardsGoal shouldBe 1
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }

            it("다른 deck이나 다른 user의 카드는 available 대상에 포함되지 않는다") {
                val session = createSession(
                    newCardsGoal = 5,
                    newCardsStudied = 0,
                    reviewCardsGoal = 5,
                    reviewCardsStudied = 0,
                )
                // 내 deck의 학습 대상 카드: NEW 1장, REVIEW 1장
                createCls(cardId = 1L, status = CardLearningStatus.NEW)
                createCls(
                    cardId = 2L,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = session.sessionDate,
                )
                // 다른 deck의 카드는 제외
                createCls(cardId = 3L, deckId = 2L, status = CardLearningStatus.NEW)
                createCls(
                    cardId = 4L,
                    deckId = 2L,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = session.sessionDate,
                )
                // 다른 user의 카드는 제외
                createCls(cardId = 5L, userId = 2L, status = CardLearningStatus.NEW)
                createCls(
                    cardId = 6L,
                    userId = 2L,
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = session.sessionDate,
                )

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // 내 deck 카드만 계산되므로
                persisted.newCardsGoal shouldBe 1 // newGoal = min(5, 0+1) = 1
                persisted.reviewCardsGoal shouldBe 1 // reviewGoal = min(5, 0+1) = 1
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }
        }

        context("세션 저장 tz와 클라이언트 tz이 다른 경우") {

            it("세션 tz 기준 만료지만 클라이언트 tz 기준 만료가 아니라면 goal 보정이 수행된다") {
                val southZoneId = ZoneId.of("America/Los_Angeles")
                val session = createSession(newCardsGoal = 5, reviewCardsGoal = 5)
                val travelNow = Instant.parse("2026-06-03T20:00:00Z") // la 기준으로는 유효, kst 기준으로는 만료

                studyService.adjustGoalsOnCardDeletion(now = travelNow, timezone = southZoneId, userId = USER_ID, deckId = DECK_ID)

                val adjustedSession = studySessionRepository.findByIdOrNull(session.id)!!
                adjustedSession.status shouldBe StudySessionStatus.COMPLETED
                adjustedSession.newCardsGoal shouldBe 0
                adjustedSession.reviewCardsGoal shouldBe 0
            }
        }

        context("목표 조정 후 결과 확인") {

            it("후보가 부족하면 review goal이 감소한다") {
                val session = createSession(
                    reviewCardsGoal = 10,
                    reviewCardsStudied = 5,
                )
                // 학습해야할 남은 REVIEW 4장
                repeat(4) { i ->
                    createCls(
                        i.toLong(),
                        status = CardLearningStatus.REVIEW,
                        nextReviewDate = session.sessionDate,
                    )
                }

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                // reviewGoal = min(10, 5+4) = 9
                persisted.reviewCardsGoal shouldBe 9
                persisted.status shouldBe StudySessionStatus.ACTIVE
            }

            it("후보가 전부 사라지면 goal이 studied로 내려가 세션이 COMPLETED 된다") {
                val session = createSession(
                    newCardsGoal = 10,
                    newCardsStudied = 5,
                    reviewCardsGoal = 10,
                    reviewCardsStudied = 5,
                )
                // 학습 가능한 카드 0장

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                persisted.status shouldBe StudySessionStatus.COMPLETED
                persisted.endedAt shouldBe baseNow
                persisted.newCardsGoal shouldBe 5
                persisted.reviewCardsGoal shouldBe 5
            }

            it("studied가 0인 채 후보가 전부 사라지면 goal이 0,0이 되어 세션이 COMPLETED 된다") {
                val session = createSession(
                    newCardsGoal = 3,
                    newCardsStudied = 0,
                    reviewCardsGoal = 3,
                    reviewCardsStudied = 0,
                )
                // 학습 가능한 카드 0장

                studyService.adjustGoalsOnCardDeletion(now = baseNow, timezone = kstZoneId, userId = USER_ID, deckId = DECK_ID)

                val persisted = studySessionRepository.findByIdOrNull(session.id)!!
                persisted.status shouldBe StudySessionStatus.COMPLETED
                persisted.newCardsGoal shouldBe 0
                persisted.reviewCardsGoal shouldBe 0
            }
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val DECK_ID = 1L
    }
}
