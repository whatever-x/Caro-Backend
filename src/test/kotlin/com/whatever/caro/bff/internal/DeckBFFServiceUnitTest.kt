package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.card.api.deck.DeckPresetDto
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudyApi
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
import java.time.LocalDate

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
            clearMocks(cardApi, studyApi, deckPresetApi)
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
    })
