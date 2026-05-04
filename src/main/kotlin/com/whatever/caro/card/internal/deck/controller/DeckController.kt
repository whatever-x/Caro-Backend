package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponse
import com.whatever.caro.card.internal.deck.dto.create.toDto
import com.whatever.caro.card.internal.deck.dto.create.toResponse
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/decks")
class DeckController(
    private val deckService: DeckService,
) {

    @PostMapping
    fun createDeck(
        @Valid @RequestBody createDeckRequest: CreateDeckRequest,
    ): ResponseEntity<ApiResponse<CreateDeckResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val deck = deckService.createDeck(userId = userId, createDeckRequest.toDto()).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(deck))
    }
}
