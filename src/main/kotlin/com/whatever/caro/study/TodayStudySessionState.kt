package com.whatever.caro.study

sealed interface TodayStudySessionState {
    data class InProgress(val session: StudySessionDto) : TodayStudySessionState
    data class Completed(val session: StudySessionDto) : TodayStudySessionState
    data class NotStarted(val pool: StudyTargetPoolCount, val presetId: Long) : TodayStudySessionState
    data object RestDay : TodayStudySessionState
}
