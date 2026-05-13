package com.whatever.caro.card.api.note

data class CardsCreatedEvent(
    val cardIds: List<Long>,
    val userId: Long,
)
