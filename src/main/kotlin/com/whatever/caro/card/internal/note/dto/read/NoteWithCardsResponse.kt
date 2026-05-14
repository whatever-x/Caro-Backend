package com.whatever.caro.card.internal.note.dto.read

data class NoteWithCardsResponse(
    val id: Long,
    val fields: Map<String, String>,
)
