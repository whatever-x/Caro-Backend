package com.whatever.caro.card.internal.deck.dto.delete

import jakarta.validation.constraints.Positive

data class DeleteDeckRequest(
    @field:Positive(message = "덱 ID는 양수여야 합니다")
    val deckId: Long,
)

fun DeleteDeckRequest.toDto() = DeleteDeckDto(deckId = deckId)
