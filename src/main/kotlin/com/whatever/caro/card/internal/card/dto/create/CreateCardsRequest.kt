package com.whatever.caro.card.internal.card.dto.create

import com.whatever.caro.card.internal.card.CardService
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateCardsRequest(
    @field:Valid
    @field:NotEmpty(message = "{validation.card.items.required}")
    @field:Size(max = CardService.MAX_CARDS_PER_REQUEST, message = "{validation.card.items.max_size}")
    @Schema(description = "생성할 카드 묶음 목록", required = true)
    val items: List<CreateCardItem>,
)

data class CreateCardItem(
    @Schema(description = "카드 타입 (기본값: BASIC)", defaultValue = "BASIC")
    val cardType: CardType = CardType.BASIC,

    @field:NotEmpty(message = "{validation.card.fields.required}")
    @Schema(description = "카드 필드 (e.g. {\"front\": \"apple\", \"back\": \"사과\"})", required = true)
    val fields: Map<String, String>,
)

fun CreateCardsRequest.toDto(
    deckId: Long,
) = CreateCardsDto(
    deckId = deckId,
    items = items.map { it.toDto() },
)

fun CreateCardItem.toDto() =
    CreateCardItemDto(
        noteTypeId = cardType.noteTypeId,
        fields = fields,
    )
