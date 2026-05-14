package com.whatever.caro.card.internal.notetype

import org.springframework.data.jpa.repository.JpaRepository

interface CardTemplateRepository : JpaRepository<CardTemplate, Long> {

    fun findByNoteTypeId(
        noteTypeId: Long,
    ): List<CardTemplate>
}
