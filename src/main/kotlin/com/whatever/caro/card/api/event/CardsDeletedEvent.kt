package com.whatever.caro.card.api.event

data class CardsDeletedEvent(
    val deckId: Long,
    val deletedCount: Int,
    val userId: Long,
)
