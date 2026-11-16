package com.whatever.caro.card.api.event

data class CardChunkCreatedEvent(
    val cardIds: List<Long>,
    val deckId: Long,
    val userId: Long,
)
