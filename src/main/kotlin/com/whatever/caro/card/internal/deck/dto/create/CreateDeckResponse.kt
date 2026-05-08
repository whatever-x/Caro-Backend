package com.whatever.caro.card.internal.deck.dto.create

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "CreateDeckResponse", description = "덱 생성 응답")
data class CreateDeckResponse(
    @field:Schema(description = "덱 ID", example = "1")
    val id: Long = 0L,
    @field:Schema(description = "덱 이름", example = "TOEIC 필수 단어")
    val deckName: String = "",
    @field:Schema(description = "덱 설명", example = "직장인을 위한 핵심 어휘")
    val deckDescription: String = "",
)
