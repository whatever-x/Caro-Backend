package com.whatever.caro.card.internal.deck.dto

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
