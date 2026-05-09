package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponse
import com.whatever.caro.card.internal.deck.dto.create.toDto
import com.whatever.caro.card.internal.deck.dto.create.toResponse
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckRequest
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckResponse
import com.whatever.caro.card.internal.deck.dto.delete.toDto
import com.whatever.caro.card.internal.deck.dto.delete.toResponse
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
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

    @DeleteMapping("/{deckId}")
    fun deleteDeck(
        @Parameter(description = "덱 ID", required = true)
        @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<DeleteDeckResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val deck = deckService.deleteDeck(
            userId = userId,
            deleteDeckDto = DeleteDeckRequest(deckId = deckId).toDto(),
        ).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(deck))
    }
}
