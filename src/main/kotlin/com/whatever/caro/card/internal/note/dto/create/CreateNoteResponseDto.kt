package com.whatever.caro.card.internal.note.dto.create

data class CreateNoteResponseDto(
    val noteId: Long,
    val fields: Map<String, String>,
    val cardIds: List<Long>,
)

fun CreateNoteResponseDto.toResponse() =
    CreateNoteResponse(
        noteId = noteId,
        fields = fields,
        cardIds = cardIds,
    )
