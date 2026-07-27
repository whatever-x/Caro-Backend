package com.whatever.caro.card.internal.note

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface NoteRepository : JpaRepository<Note, Long> {

    fun findByIdAndDeletedAtIsNull(
        id: Long,
    ): Note?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Note n where n.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
