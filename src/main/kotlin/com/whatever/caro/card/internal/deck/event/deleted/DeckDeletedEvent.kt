package com.whatever.caro.card.internal.deck.event.deleted

data class DeckDeletedEvent(
    val deckId: Long,
    val userId: Long,
)
