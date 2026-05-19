package com.whatever.caro.study

data class TodaySummaryDto(
    val state: TodaySummaryState,
    val studiedCardCount: Int,
    val totalCardCount: Int,
    val sessionId: Long?,
)
