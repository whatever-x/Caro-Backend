package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository

interface DeckRepository : JpaRepository<Deck, Long> {
    fun findByUserIdAndDeletedAtIsNull(
        userId: Long,
    ): List<Deck>

    fun findByIdAndDeletedAtIsNull(
        id: Long,
    ): Deck?
}
