package com.whatever.caro.card.internal.card.dto.create

import com.whatever.caro.card.internal.card.dto.read.CardResponse

data class CreateCardsResponse(
    val items: List<CardResponse>,
)
