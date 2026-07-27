package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface DeckRepository : JpaRepository<Deck, Long> {
    @Query(
        """
        select d from Deck d
            join fetch d.deckPreset
        where d.id in :deckIds
            and d.userId = :userId
            and d.deletedAt is null
        """,
    )
    fun findAllByIdInWithPreset(
        userId: Long,
        deckIds: Collection<Long>,
    ): List<Deck>

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

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Deck d set d.cardCount = d.cardCount - :amount where d.id = :deckId")
    fun decreaseCardCount(
        deckId: Long,
        amount: Int,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Deck d set d.cardCount = d.cardCount + :amount where d.id = :deckId")
    fun increaseCardCount(
        deckId: Long,
        amount: Int,
    ): Int

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Deck d where d.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
