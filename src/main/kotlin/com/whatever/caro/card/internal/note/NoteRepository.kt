package com.whatever.caro.card.internal.note

import org.springframework.data.jpa.repository.JpaRepository

interface NoteRepository : JpaRepository<Note, Long> {

    fun findByIdAndDeletedAtIsNull(
        id: Long,
    ): Note?
}
