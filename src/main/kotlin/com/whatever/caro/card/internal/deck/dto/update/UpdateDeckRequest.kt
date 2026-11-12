package com.whatever.caro.card.internal.deck.dto.update

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateDeckRequest(
    @field:NotBlank(message = "{validation.deck.name.required}")
    @Schema(description = "덱 이름", required = true)
    val name: String,

    @field:NotBlank(message = "{validation.deck.description.required}")
    @Schema(description = "덱 설명", required = true)
    val description: String,
)

fun UpdateDeckRequest.toDto(
    deckId: Long,
) = UpdateDeckDto(
    deckId = deckId,
    name = name,
    description = description,
)
