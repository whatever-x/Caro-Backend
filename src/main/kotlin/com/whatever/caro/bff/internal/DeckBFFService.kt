package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudyApi
import org.springframework.stereotype.Service

@Service
class DeckBFFService(
    private val cardApi: CardApi,
    private val studyApi: StudyApi,
    private val deckPresetApi: DeckPresetApi,
) {
    fun getCardsWithLearningState(
        userId: Long,
        deckId: Long,
    ): List<DeckCardItem> {
        val cards = cardApi.getCardContentsByDeck(
            userId = userId,
            deckId = deckId,
        ).takeIf { it.isNotEmpty() } ?: return emptyList()

        val learningStateByCardId = studyApi.getLearningStates(
            userId = userId,
            cardIds = cards.map { it.cardId },
        )
        val deckPreset = deckPresetApi.getLatestDeckPresetByUser(deckId, userId)

        return cards.map { card ->
            toDeckCardItem(
                card = card,
                state = learningStateByCardId[card.cardId],
                hardBadgeThreshold = deckPreset.hardBadgeThreshold,
            )
        }
    }
}

private fun toDeckCardItem(
    card: CardContentDto,
    state: CardLearningStateDto?,
    hardBadgeThreshold: Int,
): DeckCardItem =
    DeckCardItem(
        cardId = card.cardId,
        fields = card.fields,

        // state가 생성되지 않았을 경우 NEW 카드로 반환
        badge = state?.toBadge(hardBadgeThreshold) ?: CardLearningStateBadge.NEW,
        reviewCount = state?.totalReviews ?: 0,
    )

private fun CardLearningStateDto.toBadge(
    hardBadgeThreshold: Int,
): CardLearningStateBadge {
    // SUSPENDED를 먼저 차단
    val baseBadge = when (status) {
        CardLearningStatus.NEW -> CardLearningStateBadge.NEW
        CardLearningStatus.REVIEW -> CardLearningStateBadge.REVIEW
        CardLearningStatus.SUSPENDED -> error("SUSPENDED status is not supported")
    }

    if (consecutiveAgainCount >= hardBadgeThreshold) {
        return CardLearningStateBadge.HARD
    }
    return baseBadge
}
