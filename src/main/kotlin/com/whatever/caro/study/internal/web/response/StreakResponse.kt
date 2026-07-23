package com.whatever.caro.study.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "연속 학습(streak) 응답")
data class StreakResponse(
    @get:Schema(description = "연속 학습 상태")
    val status: StreakStatus,
    @get:Schema(description = "오늘 기준 현재 연속 학습일 수, status가 ACTIVE가 아니면 항상 0이다", example = "5")
    val currentStreak: Int,
)

@Schema(
    description = """
    연속 학습 상태
    - NOT_STARTED: 학습 이력이 전혀 없음, currentStreak은 항상 0
    - ACTIVE: streak이 유지되는 중. 오늘 아직 학습하지 않았어도 어제까지 이어졌다면 유지됨, currentStreak은 현재 연속 일수
    - BROKEN: 이어지던 streak이 끊긴 상태, 다시 학습을 완료하면 1일차부터 시작함, currentStreak은 항상 0
    """,
)
enum class StreakStatus {
    NOT_STARTED,
    ACTIVE,
    BROKEN,
}
