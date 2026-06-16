package com.whatever.caro.bff.internal.web.response

import com.whatever.caro.bff.internal.CardLearningStateBadge

data class DeckCardResponse(
    val cardId: Long,
    val fields: Map<String, String>,
    val badge: CardLearningStateBadge,
    val reviewCount: Int,
)
