package com.whatever.caro.card.api.deck

data class DeckDeletedEvent(
    val deckId: Long,
    val userId: Long,
)
