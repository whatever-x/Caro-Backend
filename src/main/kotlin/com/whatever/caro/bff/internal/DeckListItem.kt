package com.whatever.caro.bff.internal

import com.whatever.caro.study.TodaySummaryState

data class DeckListItem(
    val deckId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
    val progress: StudySessionProgress,
)

data class StudySessionProgress(
    val state: TodaySummaryState,
    val sessionId: Long?,
    val studiedCardCount: Int,
    val totalCardCount: Int,
)
