package com.whatever.caro.card.internal.card.dto.update

data class UpdateCardResponseDto(
    val cardId: Long,
    val fields: Map<String, String>,
)

fun UpdateCardResponseDto.toResponse() =
    UpdateCardResponse(
        cardId = cardId,
        fields = fields,
    )
