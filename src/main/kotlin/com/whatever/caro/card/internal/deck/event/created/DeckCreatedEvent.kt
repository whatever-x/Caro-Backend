package com.whatever.caro.card.internal.deck.event.created

data class DeckCreatedEvent(
    val deckId: Long,
    val userId: Long,
)
