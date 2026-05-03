package com.whatever.caro.card.internal.card

import org.springframework.stereotype.Service

@Service
class CardService(
    private val cardRepository: CardRepository,
) {
    fun getCardsByNoteId(
        noteId: Long,
    ): List<Card> =
        cardRepository.findByNoteId(noteId) ?: throw IllegalArgumentException("Card not found with id: $noteId")
}
