package com.whatever.caro.card.internal.card.dto.update

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

data class UpdateCardRequest(
    @field:NotEmpty(message = "필드는 비어있을 수 없습니다")
    @Schema(description = "수정할 카드 필드", required = true)
    val fields: Map<String, String>,
)

fun UpdateCardRequest.toDto(
    cardId: Long,
) = UpdateCardDto(
    cardId = cardId,
    fields = fields,
)
