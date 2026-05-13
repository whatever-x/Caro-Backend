package com.whatever.caro.card.internal.note.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class CreateNoteRequest(
    @field:Positive(message = "노트 타입 ID는 양수여야 합니다")
    @Schema(description = "노트 타입 ID (e.g. Basic Bi-directional)", required = true)
    val noteTypeId: Long,

    @field:NotEmpty(message = "필드는 비어있을 수 없습니다")
    @Schema(description = "노트 필드 (e.g. {\"front\": \"apple\", \"back\": \"사과\"})", required = true)
    val fields: Map<String, String>,
)

fun CreateNoteRequest.toDto(deckId: Long) =
    CreateNoteDto(
        deckId = deckId,
        noteTypeId = noteTypeId,
        fields = fields,
    )
