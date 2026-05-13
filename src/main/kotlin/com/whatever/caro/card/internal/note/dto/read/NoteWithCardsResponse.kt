package com.whatever.caro.card.internal.note.dto.read

data class NoteWithCardsResponse(
    val noteId: Long,
    val fields: Map<String, String>,
    val cardIds: List<Long>,
)
