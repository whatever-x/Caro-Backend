package com.whatever.caro.card.internal.note.dto.read

data class NoteWithCardsResponseDto(
    val noteId: Long,
    val fields: Map<String, String>,
    val cardIds: List<Long>,
)

fun NoteWithCardsResponseDto.toResponse() =
    NoteWithCardsResponse(
        noteId = noteId,
        fields = fields,
        cardIds = cardIds,
    )
