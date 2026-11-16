package com.whatever.caro.card.internal.deck.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class CreateDeckRequest(
    @field:NotBlank(message = "{validation.deck.name.required}")
    @Schema(description = "덱 이름", required = true)
    val name: String,

    @field:NotBlank(message = "{validation.deck.description.required}")
    @Schema(description = "덱 설명", required = true)
    val description: String,
)

fun CreateDeckRequest.toDto() =
    CreateDeckDto(
        name = name,
        description = description,
    )
