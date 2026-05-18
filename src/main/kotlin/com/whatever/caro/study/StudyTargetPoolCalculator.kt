package com.whatever.caro.study

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
        val nextSessionStart = getNextSessionStart(now, timezone, dayCutoffHour)

        val newTargetCount = cardLearningStateRepository.countNewCards(
            userId = userId,
            deckId = deckId,
        )
        val reviewTargetCount = cardLearningStateRepository.countTodayReviewCards(
            userId = userId,
            deckId = deckId,
            nextSessionStart = nextSessionStart,
        )

        val newPool = min(newCardPerDay, newTargetCount)
        val reviewPool = min(reviewCardPerDay, reviewTargetCount)

        return StudyTargetPoolCount(
            newCount = newPool,
            reviewCount = reviewPool,
        )
    }

    /**
     * dayCutoff를 반영한 timezone의 `오늘`을 반환
     */
    private fun getToday(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): LocalDate = now.atZone(timezone).minusHours(dayCutoffHour.toLong()).toLocalDate()

    private fun getNextSessionStart(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): Instant =
        getToday(now, timezone, dayCutoffHour)
            .plusDays(1)
            .atTime(dayCutoffHour, 0)
            .atZone(timezone)
            .toInstant()
}

data class StudyTargetPoolCount(
    val newCount: Int,
    val reviewCount: Int,
)
