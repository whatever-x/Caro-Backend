package com.whatever.caro.card.internal.deck.dto.delete

data class DeleteDeckResponse(
    val id: Long,
)

fun DeleteDeckResponseDto.toResponse() = DeleteDeckResponse(id = id)
