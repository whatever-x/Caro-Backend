package com.whatever.caro.card.internal.note.dto.delete

data class DeleteNoteResponseDto(
    val noteId: Long,
)

fun DeleteNoteResponseDto.toResponse() =
    DeleteNoteResponse(id = noteId)
