package com.whatever.caro.card.internal.deck

import com.whatever.caro.card.api.deck.DeckInfoResponse

fun Deck.toInfo() =
    DeckInfoResponse(
        id = id,
        userId = userId,
        name = name,
        description = description,
        cardCount = cardCount,
    )
