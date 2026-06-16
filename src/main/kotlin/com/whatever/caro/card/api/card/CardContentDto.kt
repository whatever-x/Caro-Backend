package com.whatever.caro.card.api.card

data class CardContentDto(
    val cardId: Long,
    val fields: Map<String, String>,
)
