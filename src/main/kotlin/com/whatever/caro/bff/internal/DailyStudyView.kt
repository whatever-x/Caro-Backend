package com.whatever.caro.bff.internal

sealed interface DailyStudyView {
    data class InProgressDto(
        val sessionId: Long,
        val studiedCardCount: Int,
        val totalCardCount: Int,
        val cards: List<StudyCardItem>,
    ) : DailyStudyView

    data class CompletedDto(
        val sessionId: Long,
        val totalCardCount: Int,
        val studiedCardCount: Int,
    ) : DailyStudyView

    data object RestDayDto : DailyStudyView
}

data class StudyCardItem(
    val cardId: Long,
    val fields: Map<String, String>,
)
