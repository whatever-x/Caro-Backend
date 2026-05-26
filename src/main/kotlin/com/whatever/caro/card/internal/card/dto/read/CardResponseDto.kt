package com.whatever.caro.card.internal.card.dto.read

data class CardResponseDto(
    val cardId: Long,
    val fields: Map<String, String>,
)

fun CardResponseDto.toResponse() =
    CardResponse(
        cardId = cardId,
        fields = fields,
    )
