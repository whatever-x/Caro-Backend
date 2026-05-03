package com.whatever.caro.card.internal.deck.event

data class DeckCreatedEvent(
    val deckId: Long,
    val userId: Long,
)
