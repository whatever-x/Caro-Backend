package com.whatever.caro.study.internal.web.response

import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.TodaySummaryState

data class TodayStudySummaryResponse(
    val sessionId: Long?,
    val state: TodaySummaryState,
    val studiedCardCount: Int,
    val totalCardCount: Int,
) {
    companion object {
        fun from(state: TodayStudySessionState): TodayStudySummaryResponse = when (state) {
            is TodayStudySessionState.Completed -> state.session.toSummary(TodaySummaryState.COMPLETED)
            is TodayStudySessionState.InProgress -> state.session.toSummary(TodaySummaryState.IN_PROGRESS)
            is TodayStudySessionState.NotStarted -> TodayStudySummaryResponse(
                sessionId = null,
                state = TodaySummaryState.NOT_STARTED,
                studiedCardCount = 0,
                totalCardCount = state.pool.newCount + state.pool.reviewCount,
            )
            is TodayStudySessionState.RestDay -> TodayStudySummaryResponse(
                sessionId = null,
                state = TodaySummaryState.REST_DAY,
                studiedCardCount = 0,
                totalCardCount = 0,
            )
        }
    }
}

private fun StudySessionDto.toSummary(state: TodaySummaryState) = TodayStudySummaryResponse(
    sessionId = sessionId,
    state = state,
    studiedCardCount = newCardsStudied + reviewCardsStudied,
    totalCardCount = estimatedTotal,
)
