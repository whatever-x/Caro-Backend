package com.whatever.caro.card.internal.note.dto.create

data class CreateNoteDto(
    val deckId: Long,
    val noteTypeId: Long,
    val fields: Map<String, String>,
)
