package com.whatever.caro.card.api.deck

import com.whatever.caro.card.internal.deck.Deck

interface DeckApi {
    fun getDecks(userId: Long): List<Deck>
}
