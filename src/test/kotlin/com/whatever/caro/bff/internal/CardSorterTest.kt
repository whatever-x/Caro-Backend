package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import java.time.LocalDate

class CardSorterTest :
    DescribeSpec({
        fun cardWithState(
            cardId: Long,
            lastReviewedDate: LocalDate? = null,
            totalReviews: Int = 0,
            hasState: Boolean = true,
        ): CardWithState =
            CardWithState(
                card = CardContentDto(
                    cardId = cardId,
                    fields = mapOf("front" to "Q$cardId", "back" to "A$cardId"),
                ),
                state = if (hasState) {
                    CardLearningStateDto(
                        cardId = cardId,
                        status = CardLearningStatus.NEW,
                        totalReviews = totalReviews,
                        consecutiveAgainCount = 0,
                        lastReviewedDate = lastReviewedDate,
                    )
                } else {
                    null
                },
            )

        fun List<CardWithState>.sortedIds(
            sortType: CardSortType,
        ): List<Long> = sortedWith(sortType.comparator()).map { it.card.cardId }

        describe("CREATED") {
            it("cardId 역순(최신 생성순)으로 정렬한다") {
                val cards = listOf(
                    cardWithState(cardId = 1L),
                    cardWithState(cardId = 3L),
                    cardWithState(cardId = 2L),
                )

                cards.sortedIds(CardSortType.CREATED) shouldContainExactly listOf(3L, 2L, 1L)
            }
        }

        describe("LAST_REVIEWED") {
            it("복습한 카드가 최근 복습일 역순으로 먼저 온다") {
                val cards = listOf(
                    cardWithState(cardId = 1L, lastReviewedDate = LocalDate.parse("2026-07-01")),
                    cardWithState(cardId = 2L, lastReviewedDate = LocalDate.parse("2026-07-15")),
                    cardWithState(cardId = 3L, lastReviewedDate = LocalDate.parse("2026-07-10")),
                )

                cards.sortedIds(CardSortType.LAST_REVIEWED) shouldContainExactly listOf(2L, 3L, 1L)
            }

            it("미복습 카드는 목록 끝에 cardId 역순으로 온다") {
                val cards = listOf(
                    // state가 미생성된 카드(백엔드 오류 시 발생)와, state는 있지만 복습 이력이 없는 카드 모두 미복습으로 취급
                    cardWithState(cardId = 1L, hasState = false),
                    cardWithState(cardId = 2L, lastReviewedDate = LocalDate.parse("2026-07-15")),
                    cardWithState(cardId = 3L, lastReviewedDate = null),
                )

                cards.sortedIds(CardSortType.LAST_REVIEWED) shouldContainExactly listOf(2L, 3L, 1L)
            }

            it("같은 날 복습한 카드끼리는 cardId 역순으로 정렬한다") {
                val sameDay = LocalDate.parse("2026-07-15")
                val cards = listOf(
                    cardWithState(cardId = 1L, lastReviewedDate = sameDay),
                    cardWithState(cardId = 3L, lastReviewedDate = sameDay),
                    cardWithState(cardId = 2L, lastReviewedDate = sameDay),
                )

                cards.sortedIds(CardSortType.LAST_REVIEWED) shouldContainExactly listOf(3L, 2L, 1L)
            }
        }

        describe("REVIEW_FREQUENCY") {
            it("totalReviews 역순으로 정렬한다") {
                val cards = listOf(
                    cardWithState(cardId = 1L, totalReviews = 3),
                    cardWithState(cardId = 2L, totalReviews = 10),
                    cardWithState(cardId = 3L, totalReviews = 5),
                )

                cards.sortedIds(CardSortType.REVIEW_FREQUENCY) shouldContainExactly listOf(2L, 3L, 1L)
            }

            it("state가 없는 카드는 0회로 취급되어 뒤로 간다") {
                val cards = listOf(
                    cardWithState(cardId = 1L, hasState = false),
                    cardWithState(cardId = 2L, totalReviews = 1),
                )

                cards.sortedIds(CardSortType.REVIEW_FREQUENCY) shouldContainExactly listOf(2L, 1L)
            }

            it("복습 횟수가 같은 카드끼리는 cardId 역순으로 정렬한다") {
                val sameReviewCount = 10
                val cards = listOf(
                    cardWithState(cardId = 1L, totalReviews = sameReviewCount),
                    cardWithState(cardId = 3L, totalReviews = sameReviewCount),
                    cardWithState(cardId = 2L, totalReviews = sameReviewCount),
                )

                cards.sortedIds(CardSortType.LAST_REVIEWED) shouldContainExactly listOf(3L, 2L, 1L)
            }
        }

        context("공통 tiebreaker") {
            withData(
                nameFn = { "sortType=$it -> 조건이 같을 경우 cardId 역순으로 재정렬" },
                CardSortType.entries.toList(),
            ) { sortType ->
                // 모든 정렬 기준 값이 동일한 카드들
                val cards = listOf(
                    cardWithState(cardId = 2L),
                    cardWithState(cardId = 1L),
                    cardWithState(cardId = 3L),
                )

                cards.sortedIds(sortType) shouldContainExactly listOf(3L, 2L, 1L)
            }
        }
    })
