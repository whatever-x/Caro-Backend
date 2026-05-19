package com.whatever.caro.card.internal.deck.dto.update

data class UpdateDeckDto(
    val deckId: Long,
    val name: String,
    val description: String,
)
