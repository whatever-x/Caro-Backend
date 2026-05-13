package com.whatever.caro.card.internal.note.dto.update

data class UpdateNoteDto(
    val noteId: Long,
    val fields: Map<String, String>,
)
