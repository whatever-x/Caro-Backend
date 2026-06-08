package com.whatever.caro.study.internal.event

import com.whatever.caro.card.api.event.CardsCreatedEvent
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.study.internal.StudyService
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
internal class CardLearningStateEventListener(
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val studyService: StudyService,
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
        val orphans = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
            userId = event.userId,
            cardIds = event.deletedCardIds,
        ).takeIf { it.isNotEmpty() } ?: return
        orphans.forEach { it.softDelete(event.deletedAt) }
        cardLearningStateRepository.saveAll(orphans)  // flush orphans

        studyService.adjustGoalsOnCardDeletion(
            now = event.deletedAt,
            userId = event.userId,
            deckId = event.deckId,
        )
    }
}
