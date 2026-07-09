package com.whatever.caro.study.internal.streak

import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
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
     * dayCutoff를 반영한 "오늘"의 다음 세션 시작일.
     *
     * 오늘 학습일의 배타적 상한이므로 `오늘 + 1일`이다.
     */
    private fun getNextSessionStart(
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): LocalDate =
        now.atZone(timezone)
            .minusHours(dayCutoffHour.toLong())
            .toLocalDate()
            .plusDays(1)
}
