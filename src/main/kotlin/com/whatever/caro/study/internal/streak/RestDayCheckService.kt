package com.whatever.caro.study.internal.streak

import java.time.Instant
import java.time.ZoneId

/**
 * streak용 휴식일 판정 추상화.
 *
 * "사용자가 오늘 학습할 카드가 하나도 없는가"를 반환한다.
 * 카드가 전혀 없을 경우 휴식일이 아니다.
 */
interface RestDayCheckService {
    fun isRestDay(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        dayCutoffHour: Int,
    ): Boolean
}
