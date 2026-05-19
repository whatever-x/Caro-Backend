package com.whatever.caro.study

data class CardLearningStateDto(
    val cardId: Long,
    val totalReviews: Int,
    val consecutiveAgainCount: Int,
)
