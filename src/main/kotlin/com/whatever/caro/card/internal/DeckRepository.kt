package com.whatever.caro.card.internal

import org.springframework.data.jpa.repository.JpaRepository

interface DeckRepository : JpaRepository<Deck, Long>
