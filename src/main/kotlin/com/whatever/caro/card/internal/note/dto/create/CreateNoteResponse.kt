package com.whatever.caro.card.internal.note.dto.create

data class CreateNoteResponse(
    val id: Long,
    val fields: Map<String, String>,
)
