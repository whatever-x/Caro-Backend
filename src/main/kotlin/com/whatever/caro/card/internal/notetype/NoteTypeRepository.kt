package com.whatever.caro.card.internal.notetype

import org.springframework.data.jpa.repository.JpaRepository

interface NoteTypeRepository : JpaRepository<NoteType, Long>
