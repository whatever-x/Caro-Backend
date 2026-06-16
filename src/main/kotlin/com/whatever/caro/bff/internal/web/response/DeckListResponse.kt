package com.whatever.caro.bff.internal.web.response

import com.whatever.caro.study.TodaySummaryState

data class DeckListResponse(
    val deckId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
    val progress: StudySessionProgressResponse,
)

data class StudySessionProgressResponse(
    val state: TodaySummaryState,
    val sessionId: Long?,
    val studiedCardCount: Int,
    val totalCardCount: Int,
)
