package com.whatever.caro.card.internal.deck

import com.whatever.caro.card.api.deck.DeckApi
import org.springframework.stereotype.Service

@Service
class DeckService(
    private val deckRepository: DeckRepository,
) : DeckApi {
    override fun getDecks(userId: Long): List<Deck> {
        return deckRepository.findByUserId(userId) ?: error("에러처리")
    }
}
