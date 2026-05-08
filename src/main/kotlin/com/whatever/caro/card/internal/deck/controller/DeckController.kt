package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponse
import com.whatever.caro.card.internal.deck.dto.create.toDto
import com.whatever.caro.card.internal.deck.dto.create.toResponse
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Deck", description = "단어/표현 덱 관리")
@RestController
@RequestMapping("/v1/decks")
class DeckController(
    private val deckService: DeckService,
) {

    @Operation(
        summary = "덱 생성",
        description = "새 덱을 생성하고 기본 프리셋을 연결한다.",
    )
    @PostMapping
    fun createDeck(
        @Valid @RequestBody createDeckRequest: CreateDeckRequest,
    ): ResponseEntity<ApiResponse<CreateDeckResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val deck = deckService.createDeck(userId = userId, createDeckRequest.toDto()).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(deck))
    }
}
