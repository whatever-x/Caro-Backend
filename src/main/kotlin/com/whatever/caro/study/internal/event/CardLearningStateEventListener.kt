package com.whatever.caro.study.internal.event

import com.whatever.caro.card.api.event.CardsCreatedEvent
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

@Component
internal class CardLearningStateEventListener(
    private val clock: Clock,
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

    @ApplicationModuleListener
    fun onCardDeleted(
        event: CardsDeletedEvent,
    ) {
        val now = Instant.now(clock)
        val orphans = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
            userId = event.userId,
            cardIds = event.deletedCardIds,
        )
        orphans.forEach { it.softDelete(now) }
    }
}
