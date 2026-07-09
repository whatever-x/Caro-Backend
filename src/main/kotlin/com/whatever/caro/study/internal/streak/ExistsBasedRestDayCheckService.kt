package com.whatever.caro.study.internal.streak

import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

/**
 * EXISTS 쿼리 기반 휴식일 판정.
 */
@Component
class ExistsBasedRestDayCheckService(
    private val cardLearningStateRepository: CardLearningStateRepository,
) : RestDayCheckService {

    @Transactional(readOnly = true)
    override fun isRestDay(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        dayCutoffHour: Int,
    ): Boolean {
        val newCardExists = cardLearningStateRepository.existsNewCardByUser(userId)
        if (newCardExists) {
            return false
        }
        val reviewCardExists = cardLearningStateRepository.existsTodayReviewCardByUser(
            userId = userId,
            nextSessionStart = getNextSessionStart(now, timezone, dayCutoffHour),
        )
        if (reviewCardExists) {
            return false
        }

        return cardLearningStateRepository.existsByUserIdAndDeletedAtIsNull(userId)
    }

    /**
     * dayCutoff를 반영한 "오늘"의 다음 세션 시작 시각.
     */
    private fun getNextSessionStart(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): Instant =
        now.atZone(timezone)
            .minusHours(dayCutoffHour.toLong())
            .toLocalDate()
            .plusDays(1)
            .atTime(dayCutoffHour, 0)
            .atZone(timezone)
            .toInstant()
}
