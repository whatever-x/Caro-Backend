package com.whatever.caro.card.internal.deck.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class CreateDeckRequest(
    @field:NotBlank(message = "덱 이름은 필수 입니다")
    @Schema(description = "덱 이름", required = true)
    val name: String,

    @field:NotBlank(message = "덱 설명은 필수 입니다")
    @Schema(description = "덱 설명", required = true)
    val description: String,
)

fun CreateDeckRequest.toDto() =
    CreateDeckDto(
        name = name,
        description = description,
    )
