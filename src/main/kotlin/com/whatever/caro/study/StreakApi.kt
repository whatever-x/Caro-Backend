package com.whatever.caro.study

import java.time.LocalDate

interface StreakApi {
    /**
     * [today] 기준으로 오늘 아직 학습(또는 휴식) 기록이 없어 자정이 지나면 끊기는 streak을 찾는다.
     * 마지막 기록일이 어제이고 현재 streak이 1 이상인 유저만 대상이다.
     *
     * @param today 유저 타임존 기준 오늘 날짜 (dayCutoffHour = 0)
     * @return userId → 현재 streak
     */
    fun findStreaksAtRisk(
        userIds: Collection<Long>,
        today: LocalDate,
    ): Map<Long, Int>
}
