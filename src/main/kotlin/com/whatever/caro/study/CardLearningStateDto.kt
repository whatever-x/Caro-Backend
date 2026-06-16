package com.whatever.caro.study

data class CardLearningStateDto(
    val cardId: Long,
    val status: CardLearningStatus,
    val totalReviews: Int,
    val consecutiveAgainCount: Int,
)
