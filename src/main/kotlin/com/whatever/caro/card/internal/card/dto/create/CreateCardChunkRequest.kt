package com.whatever.caro.card.internal.card.dto.create

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

data class CreateCardChunkRequest(
    @field:Valid
    @field:NotEmpty(message = "생성할 카드가 비어있을 수 없습니다")
    @field:Size(max = MAX_ITEMS, message = "한 번에 생성할 수 있는 카드는 최대 {max}개입니다")
    @Schema(description = "생성할 카드 묶음 목록 (배열 순서대로 저장)", required = true)
    val items: List<CreateCardItem>,
) {
    companion object {
        const val MAX_ITEMS = 1000
    }
}

fun CreateCardChunkRequest.toDto(
    deckId: Long,
) = CreateCardsDto(
    deckId = deckId,
    items = items.map { it.toDto() },
)
