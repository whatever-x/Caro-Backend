package com.whatever.caro.card.internal.card

import java.time.Instant

data class CardOwnership(
    val id: Long,
    val userId: Long,
    val deletedAt: Instant?,
)
