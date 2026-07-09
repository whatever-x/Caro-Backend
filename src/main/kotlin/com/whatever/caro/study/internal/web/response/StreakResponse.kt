package com.whatever.caro.study.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "연속 학습(streak) 응답")
data class StreakResponse(
    @get:Schema(description = "오늘 기준 현재 연속 학습일 수", example = "5")
    val currentStreak: Int,
)
