package com.whatever.caro.study

data class TodaySummaryDto(
    val state: TodaySummaryState,
    val studiedCardCount: Int,
    val totalCardCount: Int,
    val sessionId: Long?,
) {
    companion object {
        fun notStarted() = TodaySummaryDto(TodaySummaryState.NOT_STARTED, 0, 0, null)
    }
}
