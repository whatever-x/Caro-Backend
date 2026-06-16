package com.whatever.caro.bff.internal

data class DeckCardItem(
    val cardId: Long,
    val fields: Map<String, String>,
    val badge: CardLearningStateBadge,
    val reviewCount: Int,
)
