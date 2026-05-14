package com.whatever.caro.card.internal.deck.event.deleted

import com.whatever.caro.card.api.note.CardsDeletedEvent
import com.whatever.caro.card.internal.deck.DeckRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
internal class CardsDeletedEventListener(
    private val deckRepository: DeckRepository,
) {
    @ApplicationModuleListener
    fun onCardsDeleted(event: CardsDeletedEvent) {
        val deck = deckRepository.findById(event.deckId).getOrNull() ?: return
        deck.cardCount = maxOf(0, deck.cardCount - event.deletedCount)
    }
}
