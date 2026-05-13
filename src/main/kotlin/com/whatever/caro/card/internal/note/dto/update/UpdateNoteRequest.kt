package com.whatever.caro.card.internal.note.dto.update

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

data class UpdateNoteRequest(
    @field:NotEmpty(message = "필드는 비어있을 수 없습니다")
    @Schema(description = "수정할 노트 필드", required = true)
    val fields: Map<String, String>,
)

fun UpdateNoteRequest.toDto(noteId: Long) =
    UpdateNoteDto(
        noteId = noteId,
        fields = fields,
    )
