package com.whatever.caro.card.internal.deck.dto.update

data class UpdateDeckResponseDto(
    val id: Long,
    val name: String,
    val description: String,
)

data class UpdateDeckResponse(
    val id: Long,
    val deckName: String,
    val deckDescription: String,
)

fun UpdateDeckResponseDto.toResponse() =
    UpdateDeckResponse(
        id = id,
        deckName = name,
        deckDescription = description,
    )
