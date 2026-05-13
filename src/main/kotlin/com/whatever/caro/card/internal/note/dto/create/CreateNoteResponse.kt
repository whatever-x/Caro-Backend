package com.whatever.caro.card.internal.note.dto.create

data class CreateNoteResponse(
    val noteId: Long,
    val fields: Map<String, String>,
    val cardIds: List<Long>,
)
