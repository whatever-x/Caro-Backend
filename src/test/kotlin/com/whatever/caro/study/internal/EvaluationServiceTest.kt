package com.whatever.caro.study.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.exception.SessionNotActiveException
import com.whatever.caro.study.exception.SessionNotFoundException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.streak.RestDayCheckService
import com.whatever.caro.study.internal.streak.StreakStateRepository
import com.whatever.caro.study.internal.streak.StudyDayRepository
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.bigdecimal.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.test.ApplicationModuleTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// Kotest DescribeSpec의 생성자 주입에서는 @MockitoBean/@TestBean 사용이 제한적이라 @TestConfiguration + @Primary로 Bean 교체
// card 모듈의 DeckPresetApi와 study의 RestDayCheckService를 mock 으로 대체하고 나머지 레포지토리는 실제 JPA 를 사용
@TestConfiguration
class MockDeckPresetApiConfig {
    @Bean
    @Primary
    fun deckPresetApi(): DeckPresetApi = mockk(relaxed = true)

    @Bean
    @Primary
    fun restDayCheckService(): RestDayCheckService = mockk(relaxed = true)
}

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class EvaluationServiceTest(
    private val evaluationService: EvaluationService,
    private val deckPresetApi: DeckPresetApi,
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val studyDayRepository: StudyDayRepository,
    private val streakStateRepository: StreakStateRepository,
) : DescribeSpec({

    val southZoneId = ZoneId.of("America/Los_Angeles")
    val kstZoneId = ZoneId.of("Asia/Seoul")
    val eastZoneId = ZoneId.of("Pacific/Auckland")
    val dayCutoffHour = 4
    val baseNow: Instant = LocalDateTime.parse("2026-06-03T10:00:00").atZone(kstZoneId).toInstant()
    val yesterday: Instant = baseNow.minus(1, ChronoUnit.DAYS)

    afterEach {
        reviewLogRepository.deleteAllInBatch()
        studySessionRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
        studyDayRepository.deleteAllInBatch()
        streakStateRepository.deleteAllInBatch()
        clearMocks(deckPresetApi)
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

    fun getEvaluatedCardDto(
        cardId: Long,
        rating: Rating = Rating.FAIR,
        timeMs: Int = 1000,
    ): EvaluatedCardDto =
        EvaluatedCardDto(
            cardId = cardId,
            rating = rating,
            timeMs = timeMs,
        )

    fun stubPreset() {
        every { deckPresetApi.getDeckPresetById(any()) } returns Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE
    }

    describe("evaluate") {
        context("정상 상황일 때") {
            it("정상 단일 NEW 평가 시 ReviewLog가 1건 저장되고 evaluatedItems에 ValidatedItem이 담겨 반환된다") {
                stubPreset()
                val session = createSession()
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW)

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.FAIR)),
                )

                val logs = reviewLogRepository.findAllByStudySessionId(session.id)
                logs.size shouldBe 1
                logs[0].reviewType shouldBe ReviewType.NEW
                logs[0].previousCardStatus shouldBe CardLearningStatus.NEW
                logs[0].cardId shouldBe cls.cardId

                result.evaluatedItems.size shouldBe 1
                result.failedItems.size shouldBe 0
            }

            it("NEW + REVIEW 다중 평가 시 각 상태에 맞는 로그가 생성되고 세션의 학습한 카드 개수가 증가한다") {
                stubPreset()
                val session = createSession(newCardsGoal = 5, reviewCardsGoal = 5)
                val cls1 = createCls(cardId = 1L, status = CardLearningStatus.NEW)
                val cls2 = createCls(cardId = 2L, status = CardLearningStatus.REVIEW, intervalDays = 10)

                evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(
                        getEvaluatedCardDto(cardId = 1L, rating = Rating.FAIR),
                        getEvaluatedCardDto(cardId = 2L, rating = Rating.EASY),
                    ),
                )

                val logs = reviewLogRepository.findAllByStudySessionId(session.id)
                logs.size shouldBe 2
                logs.find { it.cardId == cls1.cardId }!!.reviewType shouldBe ReviewType.NEW
                logs.find { it.cardId == cls2.cardId }!!.reviewType shouldBe ReviewType.REVIEW

                val updatedSession = studySessionRepository.findByIdOrNull(session.id)!!
                updatedSession.newCardsStudied shouldBe 1
                updatedSession.reviewCardsStudied shouldBe 1

                val clss = cardLearningStateRepository.findAll()
                clss.size shouldBe 2
                clss.find { it.cardId == cls1.cardId }!!.intervalDays shouldBeGreaterThanOrEqual cls1.intervalDays
                clss.find { it.cardId == cls2.cardId }!!.intervalDays shouldBeGreaterThanOrEqual cls2.intervalDays
            }

            it("평가가 완료되면 CLS 상태가 변경된다") {
                stubPreset()
                val session = createSession()
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW, easeFactor = BigDecimal("2.50"))

                evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.EASY)),
                )

                val updatedCls = cardLearningStateRepository.findByIdOrNull(cls.id)!!
                updatedCls.easeFactor shouldBeGreaterThan cls.easeFactor
                updatedCls.intervalDays shouldBeGreaterThan cls.intervalDays
                updatedCls.nextReviewDate shouldBe session.sessionDate.plusDays(updatedCls.intervalDays.toLong())
                updatedCls.status shouldBe CardLearningStatus.REVIEW

                val log = reviewLogRepository.findAllByStudySessionId(session.id).first()
                log.intervalDays shouldBe updatedCls.intervalDays
                log.easeFactor shouldBeGreaterThan cls.easeFactor
            }

            it("ReviewLog의 previous* 필드는 평가 직전 CLS 스냅샷을 저장한다") {
                stubPreset()
                val session = createSession()
                val cls = createCls(
                    cardId = 1L,
                    status = CardLearningStatus.REVIEW,
                    intervalDays = 10,
                    easeFactor = BigDecimal("2.50"),
                    repetitions = 2,
                    lastReviewedDate = session.sessionDate.minusDays(10L),
                    nextReviewDate = session.sessionDate,
                )

                evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = 1L, rating = Rating.EASY)),
                )

                val log = reviewLogRepository.findAllByStudySessionId(session.id).first()
                log.previousIntervalDays shouldBe cls.intervalDays
                log.previousEaseFactor shouldBe cls.easeFactor
                log.previousCardStatus shouldBe cls.status

                log.intervalDays shouldBeGreaterThan cls.intervalDays
                log.easeFactor shouldBeGreaterThan cls.easeFactor
            }

            it("마지막 카드 평가 시 목표치에 도달하면 세션이 COMPLETED 상태가 된다") {
                stubPreset()
                val session = createSession(newCardsGoal = 1, reviewCardsGoal = 0)
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW)

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.FAIR)),
                )

                val updated = studySessionRepository.findByIdOrNull(session.id)!!
                updated.status shouldBe StudySessionStatus.COMPLETED
                updated.endedAt shouldBe baseNow
                updated.newCardsStudied shouldBe 1
                result.sessionStatus shouldBe StudySessionStatus.COMPLETED
                reviewLogRepository.findAllByStudySessionId(session.id).size shouldBe 1
            }

            it("목표치 미달 시 세션은 ACTIVE로 유지된다") {
                stubPreset()
                val session = createSession(newCardsGoal = 2, reviewCardsGoal = 0)
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW)

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.FAIR)),
                )

                val updated = studySessionRepository.findByIdOrNull(session.id)!!
                updated.status shouldBe StudySessionStatus.ACTIVE
                updated.endedAt.shouldBeNull()
                updated.newCardsStudied shouldBe 1
                result.sessionStatus shouldBe StudySessionStatus.ACTIVE
            }
        }

        context("세션에 문제가 있을 때") {
            it("존재하지 않는 sessionId면 SessionNotFoundException을 던진다") {
                shouldThrow<SessionNotFoundException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = 999L,
                        items = emptyList(),
                    )
                }
            }

            it("세션이 STOPPED이면 SessionNotActiveException을 던진다") {
                val session = createSession(status = StudySessionStatus.STOPPED)

                shouldThrow<SessionNotActiveException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("어제 COMPLETED 세션이면 SessionNotActiveException을 던진다") {
                val session = createSession(status = StudySessionStatus.COMPLETED, startedAt = yesterday)

                shouldThrow<SessionNotActiveException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("오늘 COMPLETED 세션이면 SessionNotActiveException을 던진다") {
                val session = createSession(status = StudySessionStatus.COMPLETED, startedAt = baseNow)

                shouldThrow<SessionNotActiveException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("어제 시작한 세션이 ACTIVE면 SessionExpiredException을 던진다") {
                val session = createSession(status = StudySessionStatus.ACTIVE, startedAt = yesterday)

                shouldThrow<SessionExpiredException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("다른 user의 세션 조회 시 SessionNotFoundException을 던진다") {
                val otherUserSession = createSession(userId = 2L)

                shouldThrow<SessionNotFoundException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = otherUserSession.id,
                        items = emptyList(),
                    )
                }
            }
        }

        context("평가 시 필요한 검증 규칙") {
            it("timeMs가 범위 밖이면 InvalidItem로 분류되며 평가에 반영되지 않는다") {
                stubPreset()
                val session = createSession()
                val cls1 = createCls(cardId = 1L, status = CardLearningStatus.NEW)
                val cls2 = createCls(cardId = 2L, status = CardLearningStatus.NEW)

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(
                        getEvaluatedCardDto(cardId = cls1.cardId, timeMs = -1),
                        getEvaluatedCardDto(cardId = cls2.cardId, timeMs = 600001),
                    ),
                )

                reviewLogRepository.findAllByStudySessionId(session.id).size shouldBe 0
                result.failedItems.size shouldBe 2
                result.evaluatedItems.size shouldBe 0
            }

            it("같은 세션에서 이미 평가된 cardId는 InvalidItem으로 분류되며 평가에 반영되지 않는다") {
                stubPreset()
                val session = createSession(newCardsGoal = 10)
                val cls1 = createCls(cardId = 1L, status = CardLearningStatus.NEW)
                evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls1.cardId, rating = Rating.FAIR)),
                )

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls1.cardId, rating = Rating.EASY)),
                )

                reviewLogRepository.findAllByStudySessionId(session.id).size shouldBe 1
                result.failedItems.size shouldBe 1
                result.evaluatedItems.size shouldBe 0
            }

            it("카드 평가 리스트에 중복된 카드가 존재하는 경우 마지막 아이템만 평가한다") {
                stubPreset()
                val session = createSession()
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW)

                val result = evaluationService.evaluate(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(
                        getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.AGAIN),
                        getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.EASY),
                    ),
                )

                val logs = reviewLogRepository.findAllByStudySessionId(session.id)
                logs.size shouldBe 1
                logs[0].rating shouldBe Rating.EASY

                result.evaluatedItems.size shouldBe 1
                result.failedItems.size shouldBe 0
            }
        }

        context("세션 저장 tz와 클라이언트 tz이 다른 경우") {
            it("클라이언트 tz 기준 date가 세션의 date보다 미래라면 SessionExpiredException을 던진다") {
                val session = createSession(startedAt = baseNow) // kst 26-06-03
                val clientNow = LocalDateTime.parse("2026-06-04T04:00:00").atZone(eastZoneId).toInstant()

                shouldThrow<SessionExpiredException> {
                    evaluationService.evaluate(
                        now = clientNow,
                        timezone = eastZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("클라이언트 tz 기준 date가 세션의 date보다 과거라면 SessionExpiredException을 던진다") {
                val session = createSession(startedAt = baseNow) // kst 26-06-03
                val clientNow = LocalDateTime.parse("2026-06-02T18:00:00").atZone(southZoneId).toInstant()

                shouldThrow<SessionExpiredException> {
                    evaluationService.evaluate(
                        now = clientNow,
                        timezone = southZoneId, // UTC -7이므로, kst 기준으로는 여전히 `26-06-03`
                        userId = USER_ID,
                        sessionId = session.id,
                        items = emptyList(),
                    )
                }
            }

            it("세션을 시작한 tz 기준으로는 만료지만, 클라이언트 tz 기준 date로 만료가 아니라면 정상 반영한다") {
                stubPreset()
                val session = createSession(startedAt = baseNow) // kst 26-06-03
                val cls = createCls(cardId = 1L, status = CardLearningStatus.NEW)
                val clientNow = LocalDateTime.parse("2026-06-04T04:00:00").atZone(kstZoneId).toInstant()

                val result = evaluationService.evaluate(
                    now = clientNow,
                    timezone = southZoneId, // UTC -7이므로, 클라이언트는 여전히 `26-06-03`
                    userId = USER_ID,
                    sessionId = session.id,
                    items = listOf(getEvaluatedCardDto(cardId = cls.cardId, rating = Rating.FAIR)),
                )

                result.evaluatedItems.size shouldBe 1
                result.failedItems.size shouldBe 0
            }
        }

        context("서버의 상태가 깨져 데이터 불일치가 발생한 경우") {
            it("CLS가 없는 card 평가 시 IllegalStateException을 던진고 롤백이 이뤄진다") {
                stubPreset()
                val session = createSession()
                val cls1 = createCls(cardId = 1L, status = CardLearningStatus.NEW)
                // CLS2 생성을 의도적으로 생략

                shouldThrow<IllegalStateException> {
                    evaluationService.evaluate(
                        now = baseNow,
                        timezone = kstZoneId,
                        userId = USER_ID,
                        sessionId = session.id,
                        items = listOf(
                            getEvaluatedCardDto(cardId = cls1.cardId, rating = Rating.FAIR),
                            getEvaluatedCardDto(cardId = 2L, rating = Rating.EASY),
                        ),
                    )
                }

                reviewLogRepository.count() shouldBe 0
                studySessionRepository.findByIdOrNull(session.id)!!.newCardsStudied shouldBe 0
            }
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val DECK_ID = 1L
    }
}
