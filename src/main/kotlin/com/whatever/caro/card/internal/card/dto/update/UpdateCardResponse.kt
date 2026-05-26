package com.whatever.caro.card.internal.card.dto.update

data class UpdateCardResponse(
    val cardId: Long,
    val fields: Map<String, String>,
)
