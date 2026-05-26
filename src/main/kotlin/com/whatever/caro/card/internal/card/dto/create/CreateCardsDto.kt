package com.whatever.caro.card.internal.card.dto.create

data class CreateCardsDto(
    val deckId: Long,
    val items: List<CreateCardItemDto>,
)

data class CreateCardItemDto(
    val noteTypeId: Long,
    val fields: Map<String, String>,
)
