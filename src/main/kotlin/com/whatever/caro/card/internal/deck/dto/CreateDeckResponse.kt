package com.whatever.caro.card.internal.deck.dto

data class CreateDeckResponse(
    val id: Long = 0L,
    val deckName: String = "",
    val deckDescription: String = "",
)
