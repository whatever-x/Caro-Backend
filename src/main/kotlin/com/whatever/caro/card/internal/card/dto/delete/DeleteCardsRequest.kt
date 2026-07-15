package com.whatever.caro.card.internal.card.dto.delete

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class DeleteCardsRequest(
    @field:NotEmpty
    @field:Size(max = 1000)
    @Schema(description = "카드 ID 들 (최소 1개 - 최대 1000개)", required = true)
    val cardIds: Set<Long>,
) {
    @get:AssertTrue(message = "cardIds는 모두 양수여야 합니다")
    @get:Schema(hidden = true)
    val isAllPositive: Boolean
        get() = cardIds.all { it > 0 }
}
