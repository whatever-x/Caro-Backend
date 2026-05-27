package com.whatever.caro.card

import java.math.BigDecimal

interface DeckPresetApi {
    fun getLatestDeckPresetByUser(
        deckId: Long,
        userId: Long,
    ): DeckPresetDto

    fun getDeckPresetById(
        deckPresetIdSnapshot: Long,
    ): DeckPresetDto
}

data class DeckPresetDto(
    val id: Long,
    val userId: Long?,
    val name: String,
    val newPerDay: Int,
    val newFairInterval: Int,
    val newEasyInterval: Int,
    val newInitialEaseFactor: BigDecimal,
    val reviewPerDay: Int,
    val reviewMaxInterval: Int,
    val lapseIntervalMultiplier: BigDecimal,
    val lapseMinInterval: Int,
    val leechThreshold: Int,
    val hardBadgeThreshold: Int,
)
