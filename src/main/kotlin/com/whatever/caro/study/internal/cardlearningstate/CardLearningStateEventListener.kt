package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.card.api.event.CardsCreatedEvent
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class CardLearningStateEventListener(
    private val cardLearningStateRepository: CardLearningStateRepository,
) {
    @ApplicationModuleListener
    fun onCardsCreated(
        event: CardsCreatedEvent,
    ) {
        val states = event.cardIds.map { cardId ->
            CardLearningState(cardId = cardId, deckId = event.deckId, userId = event.userId)
        }
        cardLearningStateRepository.saveAll(states)
    }
}
