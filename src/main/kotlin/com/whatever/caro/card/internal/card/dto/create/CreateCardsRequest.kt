package com.whatever.caro.card.internal.card.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty

data class CreateCardsRequest(
    @field:Valid
    @field:NotEmpty(message = "생성할 카드가 비어있을 수 없습니다")
    @Schema(description = "생성할 카드 묶음 목록", required = true)
    val items: List<CreateCardItem>,
)

data class CreateCardItem(
    @Schema(description = "카드 타입 (기본값: BASIC)", defaultValue = "BASIC")
    val cardType: CardType = CardType.BASIC,

    @field:NotEmpty(message = "필드는 비어있을 수 없습니다")
    @Schema(description = "카드 필드 (e.g. {\"front\": \"apple\", \"back\": \"사과\"})", required = true)
    val fields: Map<String, String>,
)

fun CreateCardsRequest.toDto(
    deckId: Long,
) = CreateCardsDto(
    deckId = deckId,
    items = items.map {
        CreateCardItemDto(noteTypeId = it.cardType.noteTypeId, fields = it.fields)
    },
)
