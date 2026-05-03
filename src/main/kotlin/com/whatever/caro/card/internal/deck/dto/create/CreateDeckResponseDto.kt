package com.whatever.caro.card.internal.deck.dto.create

data class CreateDeckResponseDto(
    val id: Long = 0L,
    val name: String = "",
    val description: String = "",
)

fun CreateDeckResponseDto.toResponse() = CreateDeckResponse(
    id = id,
    deckName = name,
    deckDescription = description,
)
