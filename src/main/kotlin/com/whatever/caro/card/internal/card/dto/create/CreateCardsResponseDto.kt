package com.whatever.caro.card.internal.card.dto.create

import com.whatever.caro.card.internal.card.dto.read.CardResponse
import com.whatever.caro.card.internal.card.dto.read.CardResponseDto

data class CreateCardsResponseDto(
    val items: List<CardResponseDto>,
)

fun CreateCardsResponseDto.toResponse() =
    CreateCardsResponse(
        items = items.map { CardResponse(cardId = it.cardId, fields = it.fields) },
    )
