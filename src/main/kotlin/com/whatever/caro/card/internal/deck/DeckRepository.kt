package com.whatever.caro.card.internal.deck

import org.springframework.data.jpa.repository.JpaRepository

interface DeckRepository : JpaRepository<Deck, Long> {
    fun findByUserId(userId: Long): List<Deck>?
}
