package com.whatever.caro.card.internal.note.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

data class CreateNoteRequest(
    @Schema(description = "카드 타입 (기본값: BASIC)", defaultValue = "BASIC")
    val cardType: CardType = CardType.BASIC,

    @field:NotEmpty(message = "필드는 비어있을 수 없습니다")
    @Schema(description = "카드 필드 (e.g. {\"front\": \"apple\", \"back\": \"사과\"})", required = true)
    val fields: Map<String, String>,
)

fun CreateNoteRequest.toDto(deckId: Long) =
    CreateNoteDto(
        deckId = deckId,
        noteTypeId = cardType.noteTypeId,
        fields = fields,
    )
