package com.whatever.caro.card.internal.card

import org.springframework.data.jpa.repository.JpaRepository

interface CardRepository : JpaRepository<Card, Long> {

    fun findByNoteId(noteId: Long): List<Card>?
}
