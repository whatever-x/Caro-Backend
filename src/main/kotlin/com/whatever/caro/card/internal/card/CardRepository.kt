package com.whatever.caro.card.internal.card

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CardRepository : JpaRepository<Card, Long> {

    @Query(
        "SELECT c FROM Card c " +
            "JOIN FETCH c.note " +
            "JOIN FETCH c.cardTemplate " +
            "WHERE c.id = :id AND c.deletedAt IS NULL",
    )
    fun findByIdAndDeletedAtIsNullWithNoteAndTemplate(
        id: Long,
    ): Card?

    @Query(
        "SELECT c FROM Card c " +
            "JOIN FETCH c.note " +
            "JOIN FETCH c.cardTemplate " +
            "WHERE c.deck.id = :deckId AND c.deletedAt IS NULL",
    )
    fun findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(
        deckId: Long,
    ): List<Card>

    @Query(
        """
        SELECT c FROM Card c
            JOIN FETCH c.note
            JOIN FETCH c.cardTemplate
        WHERE c.id IN :ids
            AND c.userId = :userId
            AND c.deletedAt IS NULL
    """,
    )
    fun findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(
        ids: Collection<Long>,
        userId: Long,
    ): List<Card>

    fun countByNoteIdAndDeletedAtIsNullAndIdNot(
        noteId: Long,
        id: Long,
    ): Long
}
