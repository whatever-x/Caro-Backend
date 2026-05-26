package com.whatever.caro.card.internal.card.dto.read

data class CardResponse(
    val cardId: Long,
    val fields: Map<String, String>,
)
