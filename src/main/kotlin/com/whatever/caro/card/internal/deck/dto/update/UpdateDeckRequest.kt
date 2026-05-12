package com.whatever.caro.card.internal.deck.dto.update

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class UpdateDeckRequest(
    @field:NotBlank(message = "덱 이름은 필수 입니다")
    @Schema(description = "덱 이름", required = true)
    val name: String,

    @field:NotBlank(message = "덱 설명은 필수 입니다")
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
