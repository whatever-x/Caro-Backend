package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.study.CardLearningStateDto

internal data class CardWithState(
    val card: CardContentDto,
    val state: CardLearningStateDto?,
)

internal fun CardSortType.comparator(): Comparator<CardWithState> {
    val primary: Comparator<CardWithState> = when (this) {
        CardSortType.CREATED -> Comparator { _, _ -> 0 }

        // 생성일(card id)정렬을 위해 동률로 처리
        CardSortType.LAST_REVIEWED -> compareBy(nullsLast(reverseOrder())) {
            it.state?.lastReviewedDate
        }

        CardSortType.REVIEW_FREQUENCY -> compareByDescending {
            it.state?.totalReviews ?: 0
        }
    }
    return primary.thenByDescending { it.card.cardId }
}
