package com.whatever.caro.card.internal.card.dto.delete

data class DeleteCardResponseDto(
    val cardId: Long,
)

fun DeleteCardResponseDto.toResponse() =
    DeleteCardResponse(
        cardId = cardId,
    )
