package com.whatever.caro.card.api.event

import java.time.Instant

data class CardsDeletedEvent(
    val deckId: Long,
    val deletedCount: Int,
    val userId: Long,
    val deletedCardIds: Set<Long>,
    val deletedAt: Instant,
)
