package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyTargetPoolCount
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StudyBFFServiceUnitTest :
    DescribeSpec({

        val studyApi = mockk<StudyApi>()
        val cardApi = mockk<CardApi>()
        val service = StudyBFFService(studyApi = studyApi, cardApi = cardApi)

        val now = Instant.parse("2026-06-05T00:00:00Z")
        val userId = 1L
        val deckId = 7L
        val tz = ZoneId.of("Asia/Seoul")

        fun session(
            sessionId: Long = 100L,
            estimatedTotal: Int = 20,
            newCardsStudied: Int = 0,
            reviewCardsStudied: Int = 0,
            status: StudySessionStatus = StudySessionStatus.ACTIVE,
            endedAt: Instant? = null,
        ): StudySessionDto =
            StudySessionDto(
                sessionId = sessionId,
                deckId = deckId,
                status = status,
                studyType = StudyType.DAILY,
                sessionDate = LocalDate.of(2026, 6, 5),
                newCardsStudied = newCardsStudied,
                reviewCardsStudied = reviewCardsStudied,
                newCardsGoal = 10,
                reviewCardsGoal = 10,
                estimatedTotal = estimatedTotal,
                startedAt = now,
                endedAt = endedAt,
            )

        fun cls(
            cardId: Long,
        ): CardLearningStateDto =
            CardLearningStateDto(
                cardId = cardId,
                status = CardLearningStatus.NEW,
                totalReviews = 0,
                consecutiveAgainCount = 0,
                lastReviewedDate = null,
            )

        fun content(
            cardId: Long,
            front: String = "Q$cardId",
        ): CardContentDto =
            CardContentDto(
                cardId = cardId,
                fields = mapOf("front" to front, "back" to "A$cardId"),
            )

        fun stubStartOrResumeDailyStudySession(
            state: TodayStudySessionState,
        ) {
            every {
                studyApi.startOrResumeDailyStudySession(
                    now = now,
                    userId = userId,
                    deckId = deckId,
                    studyType = StudyType.DAILY,
                    timezone = tz,
                    dayCutoffHour = 0,
                )
            } returns state
        }

        afterTest {
            clearMocks(studyApi, cardApi)
        }

        describe("startOrResumeDailyStudy") {

            context("세션 상태별 분기에 따른 매핑") {

                it("InProgress 세션이면 큐와 카드 콘텐츠를 조합해 InProgressDto를 반환한다") {
                    val session = session(estimatedTotal = 20, newCardsStudied = 3, reviewCardsStudied = 2)
                    val cardQueue = listOf(cls(1L), cls(2L), cls(3L))
                    val cardContentsById = mapOf(1L to content(1L), 2L to content(2L), 3L to content(3L))

                    stubStartOrResumeDailyStudySession(TodayStudySessionState.InProgress(session))
                    every { studyApi.getStudySessionCardQueue(userId = userId, sessionId = 100L, now = now, timezone = tz) } returns cardQueue
                    every { cardApi.getCardsByIds(userId = userId, cardIds = cardQueue.map { it.cardId }) } returns cardContentsById

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    val dto = result.shouldBeInstanceOf<DailyStudyView.InProgressDto>()
                    dto.sessionId shouldBe session.sessionId
                    dto.studiedCardCount shouldBe session.newCardsStudied + session.reviewCardsStudied
                    dto.totalCardCount shouldBe session.estimatedTotal
                    dto.cards.map { it.cardId } shouldContainExactly cardQueue.map { it.cardId }
                }

                it("Completed 세션이면 카드 조회 없이 CompletedDto를 반환한다") {
                    val session = session(status = StudySessionStatus.COMPLETED, estimatedTotal = 20, newCardsStudied = 5, reviewCardsStudied = 15)
                    stubStartOrResumeDailyStudySession(TodayStudySessionState.Completed(session))

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    val dto = result.shouldBeInstanceOf<DailyStudyView.CompletedDto>()
                    dto.sessionId shouldBe session.sessionId
                    dto.studiedCardCount shouldBe session.newCardsStudied + session.reviewCardsStudied
                    dto.totalCardCount shouldBe session.estimatedTotal
                    verify(exactly = 0) { studyApi.getStudySessionCardQueue(any(), any(), any(), any()) }
                    verify(exactly = 0) { cardApi.getCardsByIds(any(), any()) }
                }

                it("RestDay 상태면 RestDayDto를 반환한다") {
                    stubStartOrResumeDailyStudySession(TodayStudySessionState.RestDay)

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    result shouldBe DailyStudyView.RestDayDto
                    verify(exactly = 0) { studyApi.getStudySessionCardQueue(any(), any(), any(), any()) }
                    verify(exactly = 0) { cardApi.getCardsByIds(any(), any()) }
                }

                it("NotStarted 상태가 반환되면 IllegalStateException을 던진다") {
                    val session = TodayStudySessionState.NotStarted(
                        pool = StudyTargetPoolCount(newCount = 0, reviewCount = 0),
                        presetId = 1L,
                    )
                    stubStartOrResumeDailyStudySession(session)

                    // startOrResume은 설계상 NotStarted를 반환하지 않음
                    shouldThrow<IllegalStateException> {
                        service.startOrResumeDailyStudy(now, userId, deckId, tz)
                    }
                }
            }

            context("InProgress 세션의 카드 조합 규칙") {

                it("카드 콘텐츠는 큐 순서를 보존한다") {
                    stubStartOrResumeDailyStudySession(TodayStudySessionState.InProgress(session()))

                    val cardQueue = listOf(cls(3L), cls(1L), cls(2L))
                    val cardContentsById = mapOf(1L to content(1L), 2L to content(2L), 3L to content(3L))
                    every { studyApi.getStudySessionCardQueue(userId = userId, sessionId = 100L, now = now, timezone = tz) } returns cardQueue
                    // map의 key가 구성된 순서는 queue와 다를 수 있으며, 순서에 영향을 미치지 않아야함
                    every { cardApi.getCardsByIds(userId = userId, cardIds = listOf(3L, 1L, 2L)) } returns cardContentsById

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    val dto = result.shouldBeInstanceOf<DailyStudyView.InProgressDto>()
                    dto.cards.map { it.cardId } shouldBe cardQueue.map { it.cardId } // queue의 순서를 보존
                }

                it("큐가 비어 있으면 CompletedDto를 early return한다") {
                    val session = session(estimatedTotal = 20)
                    stubStartOrResumeDailyStudySession(TodayStudySessionState.InProgress(session))
                    every { studyApi.getStudySessionCardQueue(userId = userId, sessionId = 100L, now = now, timezone = tz) } returns emptyList()
                    every { cardApi.getCardsByIds(userId = userId, cardIds = emptyList()) } returns emptyMap()

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    val dto = result.shouldBeInstanceOf<DailyStudyView.CompletedDto>()
                    dto.totalCardCount shouldBe session.estimatedTotal
                    dto.studiedCardCount shouldBe (session.newCardsStudied + session.reviewCardsStudied)
                    verify(exactly = 0) { cardApi.getCardsByIds(userId = userId, cardIds = emptyList()) }
                }
            }

            context("데이터 불일치 상황") {
                it("콘텐츠가 없는 카드는 제외하고 살아있는 카드만 InProgressDto로 반환한다") {
                    val session = session(estimatedTotal = 20, newCardsStudied = 7, reviewCardsStudied = 6)
                    stubStartOrResumeDailyStudySession(TodayStudySessionState.InProgress(session))

                    val cardQueue = listOf(cls(1L), cls(2L), cls(3L))
                    val cardContentsById = mapOf(1L to content(1L), 3L to content(3L)) // 2번 카드 콘텐츠 없음(orphan)
                    every { studyApi.getStudySessionCardQueue(userId = userId, sessionId = 100L, now = now, timezone = tz) } returns cardQueue
                    every { cardApi.getCardsByIds(userId = userId, cardIds = listOf(1L, 2L, 3L)) } returns cardContentsById

                    val result = service.startOrResumeDailyStudy(now, userId, deckId, tz)

                    val dto = result.shouldBeInstanceOf<DailyStudyView.InProgressDto>()
                    dto.cards.map { it.cardId } shouldContainExactly listOf(1L, 3L) // 큐 순서를 유지하고 orphan 2L 카드는 제거
                    dto.studiedCardCount shouldBe 13
                    dto.totalCardCount shouldBe 20
                }
            }
        }
    })
