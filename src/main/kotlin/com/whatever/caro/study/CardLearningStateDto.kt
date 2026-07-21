package com.whatever.caro.study

import java.time.LocalDate

data class CardLearningStateDto(
    val cardId: Long,
    val status: CardLearningStatus,
    val totalReviews: Int,
    val consecutiveAgainCount: Int,
    val lastReviewedDate: LocalDate?,
)
