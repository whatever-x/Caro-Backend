package com.whatever.caro.card.api.note

data class CardsDeletedEvent(
    val deckId: Long,
    val deletedCount: Int,
    val userId: Long,
)
