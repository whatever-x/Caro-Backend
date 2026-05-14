package com.whatever.caro.card.internal.note.dto.update

data class UpdateNoteResponseDto(
    val noteId: Long,
    val fields: Map<String, String>,
)

fun UpdateNoteResponseDto.toResponse() =
    UpdateNoteResponse(
        id = noteId,
        fields = fields,
    )
