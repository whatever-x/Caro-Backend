package com.whatever.caro.card.api.card

interface CardApi {
    fun getCardsByIds(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardContentDto>

    fun getCardContentsByDeck(
        userId: Long,
        deckId: Long,
    ): List<CardContentDto>
}
