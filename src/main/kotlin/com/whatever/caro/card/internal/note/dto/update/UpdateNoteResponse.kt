package com.whatever.caro.card.internal.note.dto.update

data class UpdateNoteResponse(
    val noteId: Long,
    val fields: Map<String, String>,
)
