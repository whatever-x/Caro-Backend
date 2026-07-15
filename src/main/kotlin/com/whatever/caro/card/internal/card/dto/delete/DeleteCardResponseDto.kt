package com.whatever.caro.card.internal.card.dto.delete

data class DeleteCardResponseDto(
    val deletedCardsCount: Int,
)

fun DeleteCardResponseDto.toResponse() = DeleteCardResponse(deletedCardsCount = deletedCardsCount)
