package com.whatever.caro.card.internal.deck.event

import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import kotlin.jvm.optionals.getOrNull

@Component
internal class DeckPresetEventListener(
    private val deckRepository: DeckRepository,
    private val deckPresetRepository: DeckPresetRepository,
) {
    @ApplicationModuleListener
    fun onDeckCreated(
        event: DeckCreatedEvent,
    ) {
        val preset = deckPresetRepository.findById(1L).getOrNull() ?: return // 현재는 공통 한개 뿐
        val deck = deckRepository.findById(event.deckId).getOrNull() ?: return
        deck.deckPreset = preset
    }
}
