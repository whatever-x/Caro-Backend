package com.whatever.caro.study

import com.whatever.caro.card.api.deck.DeckPresetDto
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

@Component
class StudyTargetPoolCalculator(
    private val cardLearningStateRepository: CardLearningStateRepository,
) {

    @Transactional(readOnly = true)
    fun getTodayPool(
        now: Instant,
        userId: Long,
        timezone: ZoneId,
        deckId: Long,
        newCardPerDay: Int,
        reviewCardPerDay: Int,
        dayCutoffHour: Int,
    ): StudyTargetPoolCount {
        val today = getToday(now = now, timezone = timezone, dayCutoffHour = dayCutoffHour)

        val newTargetCount = cardLearningStateRepository.countNewCards(
            userId = userId,
            deckId = deckId,
        )
        val reviewTargetCount = cardLearningStateRepository.countTodayReviewCards(
            userId = userId,
            deckId = deckId,
            today = today,
        )

        val newPool = min(newCardPerDay, newTargetCount)
        val reviewPool = min(reviewCardPerDay, reviewTargetCount)

        return StudyTargetPoolCount(
            newCount = newPool,
            reviewCount = reviewPool,
        )
    }

    @Transactional(readOnly = true)
    fun getTodayPools(
        now: Instant,
        timezone: ZoneId,
        presetByDeckId: Map<Long, DeckPresetDto>,
        dayCutoffHour: Int,
    ): Map<Long, StudyTargetPoolCount> {
        if (presetByDeckId.isEmpty()) {
            return emptyMap()
        }

        val today = getToday(now = now, timezone = timezone, dayCutoffHour = dayCutoffHour)

        val deckIds = presetByDeckId.keys
        val newCardCountByDeckId = cardLearningStateRepository.countNewCardsByDeckIds(
            deckIds = deckIds,
        ).associate { it.deckId to it.count.toInt() }
        val reviewCardCountByDeckId = cardLearningStateRepository.countReviewCardsByDeckIds(
            deckIds = deckIds,
            today = today,
        ).associate { it.deckId to it.count.toInt() }

        return presetByDeckId.mapValues { (deckId, preset) ->
            StudyTargetPoolCount(
                newCount = min(preset.newPerDay, newCardCountByDeckId[deckId] ?: 0),
                reviewCount = min(preset.reviewPerDay, reviewCardCountByDeckId[deckId] ?: 0),
            )
        }
    }

    /**
     * dayCutoff를 반영한 timezone의 `오늘`을 반환
     */
    private fun getToday(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): LocalDate = now.atZone(timezone).minusHours(dayCutoffHour.toLong()).toLocalDate()
}

data class StudyTargetPoolCount(
    val newCount: Int,
    val reviewCount: Int,
)
