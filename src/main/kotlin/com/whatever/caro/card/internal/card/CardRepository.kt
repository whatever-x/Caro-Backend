package com.whatever.caro.card.internal.card

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<Card, Long> {

    fun findByNoteId(
        noteId: Long,
    ): List<Card>?

    fun findByNoteIdAndDeletedAtIsNull(
        noteId: Long,
    ): List<Card>

    @Query("SELECT c FROM Card c JOIN FETCH c.cardTemplate WHERE c.note.id = :noteId AND c.deletedAt IS NULL")
    fun findByNoteIdAndDeletedAtIsNullWithTemplate(
        noteId: Long,
    ): List<Card>

    @Query("SELECT c FROM Card c JOIN FETCH c.note WHERE c.deck.id = :deckId AND c.deletedAt IS NULL")
    fun findByDeckIdAndDeletedAtIsNullWithNote(
        deckId: Long,
    ): List<Card>

    fun findByDeckIdAndDeletedAtIsNull(
        deckId: Long,
    ): List<Card>
}
