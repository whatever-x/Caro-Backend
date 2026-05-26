package com.whatever.caro.card.api.deck

data class DeckInfoResponse(
    val id: Long,
    val userId: Long,
    val name: String,
    val description: String,
    val cardCount: Int,
)
