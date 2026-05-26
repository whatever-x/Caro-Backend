package com.whatever.caro.card.internal.card.dto.update

data class UpdateCardDto(
    val cardId: Long,
    val fields: Map<String, String>,
)
