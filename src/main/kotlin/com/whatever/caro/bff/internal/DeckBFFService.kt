package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.TodaySummaryState
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

@Service
class DeckBFFService(
    private val cardApi: CardApi,
    private val studyApi: StudyApi,
    private val deckPresetApi: DeckPresetApi,
    private val deckApi: DeckApi,
) {
    fun getCardsWithLearningState(
        userId: Long,
        deckId: Long,
        sortType: CardSortType,
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

        return cards
            .map { CardWithState(card = it, state = learningStateByCardId[it.cardId]) }
            .sortedWith(sortType.comparator())
            .map { it.toDeckCardItem(hardBadgeThreshold = deckPreset.hardBadgeThreshold) }
    }

    fun getDecksWithDailyStudySession(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
    ): List<DeckListItem> {
        val decks = deckApi.getDecks(userId).takeIf { it.isNotEmpty() } ?: return emptyList()

        val todaySummaryByDeckId = studyApi.getTodaySummaries(
            now = now,
            timezone = timezone,
            userId = userId,
            deckIds = decks.map { it.id }.toSet(),
        )

        return decks.map { deck ->
            DeckListItem(
                deckId = deck.id,
                name = deck.name,
                description = deck.description,
                cardCount = deck.cardCount,
                progress = todaySummaryByDeckId.getValue(deck.id).toDeckProgress(),
            )
        }
    }
}

private fun TodayStudySessionState.toDeckProgress(): StudySessionProgress =
    when (this) {
        is TodayStudySessionState.InProgress -> StudySessionProgress(
            state = TodaySummaryState.IN_PROGRESS,
            sessionId = session.sessionId,
            studiedCardCount = session.newCardsStudied + session.reviewCardsStudied,
            totalCardCount = session.estimatedTotal,
        )

        is TodayStudySessionState.Completed -> StudySessionProgress(
            state = TodaySummaryState.COMPLETED,
            sessionId = session.sessionId,
            studiedCardCount = session.newCardsStudied + session.reviewCardsStudied,
            totalCardCount = session.estimatedTotal,
        )

        is TodayStudySessionState.NotStarted -> StudySessionProgress(
            state = TodaySummaryState.NOT_STARTED,
            sessionId = null,
            studiedCardCount = 0,
            totalCardCount = pool.newCount + pool.reviewCount,
        )

        is TodayStudySessionState.RestDay -> StudySessionProgress(
            state = TodaySummaryState.REST_DAY,
            sessionId = null,
            studiedCardCount = 0,
            totalCardCount = 0,
        )
    }

private fun CardWithState.toDeckCardItem(
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
