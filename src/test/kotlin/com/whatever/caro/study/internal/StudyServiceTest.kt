package com.whatever.caro.study.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodaySummaryState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class StudyServiceTest(
    private val studyService: StudyService,
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
) : DescribeSpec({

    val kstZoneId = ZoneId.of("Asia/Seoul")
    val dayCutoffHour = 4

    val baseNow: Instant = LocalDateTime.parse("2026-05-19T10:00:00").atZone(kstZoneId).toInstant()
    val today: LocalDate = LocalDate.parse("2026-05-19")
    val yesterday: LocalDate = LocalDate.parse("2026-05-18")

    afterEach {
        studySessionRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
    }

    fun createSession(
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        sessionDate: LocalDate = today,
        startedAt: Instant = baseNow,
        newStudied: Int = 0,
        reviewStudied: Int = 0,
        estimatedTotal: Int = 20,
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
                sessionDate = sessionDate,
                deckPresetIdSnapshot = 1L,
                newCardsStudied = newStudied,
                reviewCardsStudied = reviewStudied,
                estimatedTotal = estimatedTotal,
            ),
        )

    fun createCls(
        cardId: Long,
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        status: CardLearningStatus = CardLearningStatus.REVIEW,
        totalReviews: Int = 0,
        consecutiveAgainCount: Int = 0,
    ): CardLearningState =
        cardLearningStateRepository.save(
            CardLearningState(
                cardId = cardId,
                deckId = deckId,
                userId = userId,
                status = status,
                totalReviews = totalReviews,
                consecutiveAgainCount = consecutiveAgainCount,
            ),
        )

    describe("getTodaySummary") {

        it("일일학습 세션이 없으면 NOT_STARTED dto를 반환한다") {
            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.state shouldBe TodaySummaryState.NOT_STARTED
        }

        it("ACTIVE 상태인 오늘 세션이면 IN_PROGRESS인 dto를 반환한다") {
            val session = createSession(
                status = StudySessionStatus.ACTIVE,
                sessionDate = today,
                newStudied = 3,
                reviewStudied = 5,
                estimatedTotal = 20,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.shouldNotBeNull()
            result.sessionId shouldBe session.id
            result.state shouldBe TodaySummaryState.IN_PROGRESS
            result.studiedCardCount shouldBe 8
            result.totalCardCount shouldBe 20
        }

        it("ACTIVE 상태이지만 오늘이 아닌 세션이면 STOPPED으로 변경되고, NOT_STARTED dto를 반환한다") {
            val staleSession = createSession(
                status = StudySessionStatus.ACTIVE,
                sessionDate = yesterday,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.state shouldBe TodaySummaryState.NOT_STARTED

            val stoppedSession = studySessionRepository.findByIdOrNull(staleSession.id)
            stoppedSession.shouldNotBeNull()
            stoppedSession.status shouldBe StudySessionStatus.STOPPED
        }

        it("COMPLETED 상태인 오늘 세션이면 COMPLETED dto를 매핑해 반환한다") {
            val session = createSession(
                status = StudySessionStatus.COMPLETED,
                sessionDate = today,
                newStudied = 10,
                reviewStudied = 10,
                estimatedTotal = 20,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.shouldNotBeNull()
            result.sessionId shouldBe session.id
            result.state shouldBe TodaySummaryState.COMPLETED
            result.studiedCardCount shouldBe 20
            result.totalCardCount shouldBe 20
            result.studiedCardCount shouldBeEqual result.totalCardCount
        }

        it("COMPLETED 상태지만 오늘이 아닌 세션이면 NOT_STARTED dto를 반환하고 status는 변경되지 않는다") {
            val session = createSession(
                status = StudySessionStatus.COMPLETED,
                sessionDate = yesterday,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.state shouldBe TodaySummaryState.NOT_STARTED

            val completedSession = studySessionRepository.findByIdOrNull(session.id)
            completedSession.shouldNotBeNull()
            completedSession.status shouldBe StudySessionStatus.COMPLETED
        }

        it("STOPPED 상태지만 오늘 세션이라면 NOT_STARTED dto를 반환한다") {
            // 정상 flow에서는 나올 수 없음
            val session = createSession(
                status = StudySessionStatus.STOPPED,
                sessionDate = today,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )
            result.state shouldBe TodaySummaryState.NOT_STARTED

            val stoppedSession = studySessionRepository.findByIdOrNull(session.id)
            stoppedSession.shouldNotBeNull()
            stoppedSession.status shouldBe StudySessionStatus.STOPPED
        }

        it("여러 세션이 있다면 가장 최신 세션을 기준으로 응답한다") {
            // 어제 세션
            createSession(
                status = StudySessionStatus.COMPLETED,
                sessionDate = yesterday,
                startedAt = yesterday.atTime(dayCutoffHour, 0).atZone(kstZoneId).toInstant(),
            )
            // 오늘 세션
            val latestSession = createSession(
                status = StudySessionStatus.ACTIVE,
                sessionDate = today,
                startedAt = baseNow,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.shouldNotBeNull()
            result.sessionId shouldBe latestSession.id
            result.state shouldBe TodaySummaryState.IN_PROGRESS
        }

        it("다른 유저나, 덱에 속한 세션은 결과에 영향을 주지 않는다") {
            createSession( // 다른 user의 오늘 ACTIVE 세션
                userId = 2L,
                deckId = DECK_ID,
                status = StudySessionStatus.ACTIVE,
                sessionDate = today,
            )
            createSession( // 다른 deck의 오늘 ACTIVE 세션
                userId = USER_ID,
                deckId = 2L,
                status = StudySessionStatus.ACTIVE,
                sessionDate = today,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result.state shouldBe TodaySummaryState.NOT_STARTED
        }
    }

    describe("getLearningStates") {

        it("매칭되는 카드는 cardId 기준 맵으로 매핑되고, 매칭되지 않는 요소는 무시한다") {
            val states = listOf(
                createCls(
                    cardId = 1L,
                    totalReviews = 3,
                    consecutiveAgainCount = 1,
                ),
                createCls(
                    cardId = 2L,
                    totalReviews = 0,
                    consecutiveAgainCount = 0,
                ),
            )

            val cardIds = states.map { it.cardId }
            val result = studyService.getLearningStates(
                userId = USER_ID,
                cardIds = cardIds + 3L,
            )

            result.keys.shouldContainExactlyInAnyOrder(cardIds)

            val firstId = cardIds.first()
            result[firstId]?.totalReviews shouldBe 3
            result[firstId]?.consecutiveAgainCount shouldBe 1

            val secondId = cardIds.last()
            result[secondId]?.totalReviews shouldBe 0
        }

        it("내 카드 학습 상태만 조회할 수 있다") {
            val states = listOf(
                createCls(
                    cardId = 1L,
                    userId = USER_ID,
                ),
                createCls(
                    cardId = 2L,
                    userId = 2L,
                ),
            )

            val result = studyService.getLearningStates(
                userId = states.first().userId,
                cardIds = states.map { it.cardId },
            )

            result.keys.shouldContainExactlyInAnyOrder(setOf(states.first().cardId))
        }

        it("조회할 card Id가 비었다면 nul을 반환한다") {
            createCls(
                cardId = 1L,
            )

            val result = studyService.getLearningStates(
                userId = USER_ID,
                cardIds = emptyList(),
            )

            result.shouldBeEmpty()
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val DECK_ID = 1L
    }
}
