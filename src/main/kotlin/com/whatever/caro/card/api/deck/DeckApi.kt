package com.whatever.caro.card.api.deck

interface DeckApi {
    fun getDecks(
        userId: Long,
    ): List<DeckInfoResponse>

    fun getDeck(
        deckId: Long,
    ): DeckInfoResponse

    fun deleteAllByUserId(
        userId: Long,
    )
}
