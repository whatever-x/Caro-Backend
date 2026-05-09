package com.whatever.caro.card.internal.deck.event.deleted

import com.whatever.caro.card.internal.deck.DeckRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Component
internal class DeckDeletedEventListener(
    private val deckRepository: DeckRepository,
) {
    @ApplicationModuleListener
    fun onDeckDeleted(
        event: DeckDeletedEvent,
    ) {
        val deck = deckRepository.findById(event.deckId).getOrNull() ?: return
        deck.softDelete(deletedAt = Instant.now())
    }
}
