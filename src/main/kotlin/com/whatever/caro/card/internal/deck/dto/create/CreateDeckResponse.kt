package com.whatever.caro.card.internal.deck.dto.create

data class CreateDeckResponse(
    val id: Long = 0L,
    val deckName: String = "",
    val deckDescription: String = "",
)
