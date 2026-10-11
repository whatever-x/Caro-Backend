package com.whatever.caro.study

import java.time.Instant
import java.time.ZoneId

interface StreakApi {
    /**
     * 오늘 아직 학습(또는 휴식) 기록이 없어 다음 날로 넘어가면 끊기는 streak을 찾는다.
     * 마지막 기록일이 어제이고 현재 streak이 1 이상인 유저만 대상이다.
     *
     * "오늘" 계산은 다른 streak API와 같은 규칙(timezone + dayCutoffHour)으로 내부에서 한다.
     */
    fun findStreaksAtRisk(
        userIds: Collection<Long>,
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int = 0,
    ): List<StreakAtRiskDto>
}

data class StreakAtRiskDto(
    val userId: Long,
    val currentStreak: Int,
    /** 오늘 학습할 카드가 없는 휴식일인지. 휴식일도 앱에 들어와야 기록되어 streak이 이어진다. */
    val isRestDay: Boolean,
)
