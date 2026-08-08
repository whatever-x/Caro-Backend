package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.api.deck.DeckInfoResponse
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.card.api.deck.DeckPresetDto
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyTargetPoolCount
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.TodaySummaryState
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DeckBFFServiceUnitTest :
    DescribeSpec({

        val cardApi = mockk<CardApi>()
        val studyApi = mockk<StudyApi>()
        val deckPresetApi = mockk<DeckPresetApi>()
        val deckApi = mockk<DeckApi>()
        val service = DeckBFFService(cardApi = cardApi, studyApi = studyApi, deckPresetApi = deckPresetApi, deckApi = deckApi)

        val userId = 1L
        val deckId = 1L

        fun createCardContent(
            cardId: Long,
        ): CardContentDto =
            CardContentDto(
                cardId = cardId,
                fields = mapOf("front" to "Q$cardId", "back" to "A$cardId"),
            )

        fun createCls(
            cardId: Long,
            status: CardLearningStatus = CardLearningStatus.NEW,
            totalReviews: Int = 0,
            consecutiveAgainCount: Int = 0,
            lastReviewedDate: LocalDate? = null,
        ): CardLearningStateDto =
            CardLearningStateDto(
                cardId = cardId,
                status = status,
                totalReviews = totalReviews,
                consecutiveAgainCount = consecutiveAgainCount,
                lastReviewedDate = lastReviewedDate,
            )

        fun createPreset(
            hardBadgeThreshold: Int = 3,
        ): DeckPresetDto =
            DeckPresetDto(
                id = 1L,
                userId = userId,
                name = "기본 프리셋",
                newPerDay = 10,
                newFairInterval = 1,
                newEasyInterval = 4,
                newInitialEaseFactor = BigDecimal("2.5"),
                reviewPerDay = 100,
                reviewMaxInterval = 365,
                lapseIntervalMultiplier = BigDecimal("0.5"),
                lapseMinInterval = 1,
                leechThreshold = 8,
                hardBadgeThreshold = hardBadgeThreshold,
            )

        fun stubDeckInformation(
            cards: List<CardContentDto>,
            states: List<CardLearningStateDto>,
            hardBadgeThreshold: Int = 3,
        ) {
            every {
                cardApi.getCardContentsByDeck(userId = userId, deckId = deckId)
            } returns cards

            every {
                studyApi.getLearningStates(userId = userId, cardIds = cards.map { it.cardId })
            } returns states.associateBy { it.cardId }

            every {
                deckPresetApi.getLatestDeckPresetByUser(deckId = deckId, userId = userId)
            } returns createPreset(hardBadgeThreshold = hardBadgeThreshold)
        }

        afterTest {
            clearMocks(cardApi, studyApi, deckPresetApi, deckApi)
        }

        describe("getCardsWithLearningState") {

            it("덱 카드들을 learningState/preset과 조합해 DeckCardItem 리스트로 매핑한다 (기본 정렬 CREATED = cardId 역순)") {
                val cards = (1L..3L).map { i -> createCardContent(i) }
                val states = listOf(
                    createCls(cardId = 1L, status = CardLearningStatus.NEW, totalReviews = 0, consecutiveAgainCount = 0),
                    createCls(cardId = 2L, status = CardLearningStatus.REVIEW, totalReviews = 3, consecutiveAgainCount = 1),
                    createCls(cardId = 3L, status = CardLearningStatus.REVIEW, totalReviews = 10, consecutiveAgainCount = 2),
                )
                stubDeckInformation(cards = cards, states = states, hardBadgeThreshold = 8)

                val result = service.getCardsWithLearningState(
                    userId = userId,
                    deckId = deckId,
                    sortType = CardSortType.CREATED,
                )

                result.map { it.cardId } shouldContainExactly listOf(3L, 2L, 1L)
                val itemByCardId = result.associateBy { it.cardId }
                states.forEach { state ->
                    val item = itemByCardId.getValue(state.cardId)
                    when (state.status) {
                        CardLearningStatus.NEW -> item.badge shouldBe CardLearningStateBadge.NEW
                        CardLearningStatus.REVIEW -> item.badge shouldBe CardLearningStateBadge.REVIEW
                        CardLearningStatus.SUSPENDED -> error("SUSPENDED status is not supported")
                    }
                    item.reviewCount shouldBe state.totalReviews
                }
            }

            context("early return 케이스") {
                it("덱에 카드가 없으면 빈 리스트를 반환하고 study/preset API를 호출하지 않는다") {
                    every { cardApi.getCardContentsByDeck(userId = userId, deckId = deckId) } returns emptyList()

                    val result = service.getCardsWithLearningState(
                        userId = userId,
                        deckId = deckId,
                        sortType = CardSortType.CREATED,
                    )

                    result.shouldBeEmpty()
                    verify(exactly = 0) { studyApi.getLearningStates(any(), any()) }
                    verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
                }
            }

            it("learningState가 없는 카드는 badge=NEW, reviewCount=0으로 반환한다") {
                val cards = listOf(createCardContent(1L), createCardContent(2L))

                // 2L 카드 누락
                val states = listOf(createCls(1L, status = CardLearningStatus.REVIEW, totalReviews = 4))
                stubDeckInformation(
                    cards = cards,
                    states = states,
                )

                val result = service.getCardsWithLearningState(
                    userId = userId,
                    deckId = deckId,
                    sortType = CardSortType.CREATED,
                )

                // CREATED 정렬(cardId 역순): [2L(state 없음), 1L]
                result[0].badge shouldBe CardLearningStateBadge.NEW
                result[0].reviewCount shouldBe 0
                result[1].badge shouldBe CardLearningStateBadge.REVIEW
                result[1].reviewCount shouldBe states.first().totalReviews
            }

            context("정렬 적용") {
                it("sortType의 정렬 기준이 적용된 순서로 반환한다") {
                    val cards = (1L..3L).map { i -> createCardContent(i) }
                    val states = listOf(
                        createCls(cardId = 1L, status = CardLearningStatus.REVIEW, totalReviews = 1, lastReviewedDate = LocalDate.parse("2026-07-10")),
                        createCls(cardId = 2L, status = CardLearningStatus.REVIEW, totalReviews = 1, lastReviewedDate = LocalDate.parse("2026-07-18")),
                        createCls(cardId = 3L, status = CardLearningStatus.REVIEW, totalReviews = 1, lastReviewedDate = LocalDate.parse("2026-07-01")),
                    )
                    stubDeckInformation(cards = cards, states = states)

                    val result = service.getCardsWithLearningState(
                        userId = userId,
                        deckId = deckId,
                        sortType = CardSortType.LAST_REVIEWED,
                    )

                    result.map { it.cardId } shouldContainExactly listOf(2L, 1L, 3L)
                }
            }

            context("badge 매핑 확인") {
                data class BadgeCase(
                    val againCount: Int,
                    val threshold: Int,
                    val status: CardLearningStatus,
                    val expected: CardLearningStateBadge,
                    val reason: String,
                )

                withData(
                    nameFn = {
                        "againCount=${it.againCount}, threshold=${it.threshold}, " +
                            "status=${it.status} -> ${it.expected} (${it.reason})"
                    },
                    BadgeCase(2, 3, CardLearningStatus.NEW, CardLearningStateBadge.NEW, "미만이면 baseBadge 유지"),
                    BadgeCase(2, 3, CardLearningStatus.REVIEW, CardLearningStateBadge.REVIEW, "미만이면 baseBadge 유지"),
                    BadgeCase(3, 3, CardLearningStatus.REVIEW, CardLearningStateBadge.HARD, "threshold==againCount"),
                    BadgeCase(4, 3, CardLearningStatus.REVIEW, CardLearningStateBadge.HARD, "threshold 초과"),
                    BadgeCase(3, 3, CardLearningStatus.NEW, CardLearningStateBadge.HARD, "NEW여도 HARD가 우선 반영"),
                    BadgeCase(0, 0, CardLearningStatus.NEW, CardLearningStateBadge.HARD, "threshold=0이면 0회도 HARD"),
                ) { (againCount, threshold, status, expected, _) ->
                    val cardId = 1L
                    val cards = listOf(createCardContent(cardId))
                    val states = listOf(createCls(cardId, status = status, consecutiveAgainCount = againCount))
                    stubDeckInformation(
                        cards = cards,
                        states = states,
                        hardBadgeThreshold = threshold,
                    )

                    val result = service.getCardsWithLearningState(userId, deckId, CardSortType.CREATED)

                    result.first().badge shouldBe expected
                }

                it("SUSPENDED status는 지원하지 않으므로 IllegalStateException을 던진다") {
                    val cardId = 1L
                    val cards = listOf(createCardContent(cardId))
                    val states = listOf(createCls(1L, status = CardLearningStatus.SUSPENDED))
                    stubDeckInformation(
                        cards = cards,
                        states = states,
                    )

                    shouldThrow<IllegalStateException> {
                        service.getCardsWithLearningState(userId, deckId, CardSortType.CREATED)
                    }
                }
            }
        }

        describe("getDeckByDeckId") {
            val now = Instant.parse("2026-08-07T00:00:00Z")
            val timezone = ZoneId.of("Asia/Seoul")

            it("덱 정보와 오늘 학습 요약을 조합해 DeckListItem 하나로 반환한다") {
                every {
                    deckApi.getDeck(userId = userId, deckId = deckId)
                } returns DeckInfoResponse(id = deckId, userId = userId, name = "내 덱", description = "설명", cardCount = 5)
                every {
                    studyApi.getTodaySummaries(now = now, timezone = timezone, userId = userId, deckIds = setOf(deckId))
                } returns mapOf(deckId to TodayStudySessionState.RestDay)

                val result = service.getDeckByDeckId(now = now, timezone = timezone, userId = userId, deckId = deckId)

                result.deckId shouldBe deckId
                result.name shouldBe "내 덱"
                result.description shouldBe "설명"
                result.cardCount shouldBe 5
                result.progress.state shouldBe TodaySummaryState.REST_DAY
            }

            it("소유권 검증은 deckApi.getDeck에 위임한다 - userId/deckId를 그대로 전달한다") {
                every {
                    deckApi.getDeck(userId = userId, deckId = deckId)
                } returns DeckInfoResponse(id = deckId, userId = userId, name = "덱", description = "", cardCount = 0)
                every {
                    studyApi.getTodaySummaries(now, timezone, userId, setOf(deckId))
                } returns mapOf(deckId to TodayStudySessionState.RestDay)

                service.getDeckByDeckId(now = now, timezone = timezone, userId = userId, deckId = deckId)

                verify(exactly = 1) { deckApi.getDeck(userId = userId, deckId = deckId) }
            }

            context("오늘 학습 상태(TodayStudySessionState)에 따라 progress를 매핑한다") {
                val sampleSession = StudySessionDto(
                    sessionId = 100L,
                    deckId = deckId,
                    status = StudySessionStatus.ACTIVE,
                    studyType = StudyType.DAILY,
                    sessionDate = LocalDate.parse("2026-08-07"),
                    newCardsStudied = 3,
                    reviewCardsStudied = 2,
                    newCardsGoal = 5,
                    reviewCardsGoal = 5,
                    estimatedTotal = 10,
                    startedAt = now,
                    endedAt = null,
                )

                data class ProgressCase(
                    val label: String,
                    val state: TodayStudySessionState,
                    val expectedState: TodaySummaryState,
                    val expectedSessionId: Long?,
                    val expectedStudied: Int,
                    val expectedTotal: Int,
                )

                withData(
                    nameFn = { it.label },
                    ProgressCase(
                        label = "InProgress -> IN_PROGRESS, 학습 수=학습한 new+review, 총=estimatedTotal",
                        state = TodayStudySessionState.InProgress(sampleSession),
                        expectedState = TodaySummaryState.IN_PROGRESS,
                        expectedSessionId = 100L,
                        expectedStudied = 5,
                        expectedTotal = 10,
                    ),
                    ProgressCase(
                        label = "Completed -> COMPLETED",
                        state = TodayStudySessionState.Completed(sampleSession),
                        expectedState = TodaySummaryState.COMPLETED,
                        expectedSessionId = 100L,
                        expectedStudied = 5,
                        expectedTotal = 10,
                    ),
                    ProgressCase(
                        label = "NotStarted -> NOT_STARTED, sessionId=null, 총=pool 합",
                        state = TodayStudySessionState.NotStarted(
                            pool = StudyTargetPoolCount(newCount = 4, reviewCount = 6),
                            presetId = 7L,
                        ),
                        expectedState = TodaySummaryState.NOT_STARTED,
                        expectedSessionId = null,
                        expectedStudied = 0,
                        expectedTotal = 10,
                    ),
                    ProgressCase(
                        label = "RestDay -> REST_DAY, 모두 0",
                        state = TodayStudySessionState.RestDay,
                        expectedState = TodaySummaryState.REST_DAY,
                        expectedSessionId = null,
                        expectedStudied = 0,
                        expectedTotal = 0,
                    ),
                ) { case ->
                    every {
                        deckApi.getDeck(userId = userId, deckId = deckId)
                    } returns DeckInfoResponse(id = deckId, userId = userId, name = "덱", description = "", cardCount = 0)
                    every {
                        studyApi.getTodaySummaries(now, timezone, userId, setOf(deckId))
                    } returns mapOf(deckId to case.state)

                    val result = service.getDeckByDeckId(now = now, timezone = timezone, userId = userId, deckId = deckId)

                    result.progress.state shouldBe case.expectedState
                    result.progress.sessionId shouldBe case.expectedSessionId
                    result.progress.studiedCardCount shouldBe case.expectedStudied
                    result.progress.totalCardCount shouldBe case.expectedTotal
                }
            }
        }
    })
