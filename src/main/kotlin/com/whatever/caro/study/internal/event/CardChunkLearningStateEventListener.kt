package com.whatever.caro.study.internal.event

import com.whatever.caro.card.api.event.CardChunkCreatedEvent
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
internal class CardChunkLearningStateEventListener(
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val clock: Clock,
) {
    @ApplicationModuleListener
    fun onCardChunkCreated(
        event: CardChunkCreatedEvent,
    ) {
        val existingCardIds = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
            userId = event.userId,
            cardIds = event.cardIds,
        ).map { it.cardId }.toSet()
        val newCardIds = event.cardIds - existingCardIds
        if (newCardIds.isEmpty()) return

        val newLearningStates = newCardIds.map { cardId ->
            CardLearningState(cardId = cardId, deckId = event.deckId, userId = event.userId)
        }
        cardLearningStateRepository.insertAll(
            states = newLearningStates,
            now = Instant.now(clock),
        )
    }
}
