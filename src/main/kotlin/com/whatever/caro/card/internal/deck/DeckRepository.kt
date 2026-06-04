package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeckRepository : JpaRepository<Deck, Long> {
    fun findByUserIdAndDeletedAtIsNull(
        userId: Long,
    ): List<Deck>

    fun findByIdAndDeletedAtIsNull(
        id: Long,
    ): Deck?

    @Query(
        """
        select d from Deck d
            left join fetch d.deckPreset
        where d.id = :deckId
            and d.deletedAt is null
        """,
    )
    fun findByIdWithPreset(
        deckId: Long,
    ): Deck?
}
