package com.whatever.caro.card.api.event

data class CardsCreatedEvent(
    val cardIds: List<Long>,
    val userId: Long,
)
