package com.whatever.caro.study.internal.event

import com.whatever.caro.card.api.event.CardsCreatedEvent
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
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
