package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.card.dto.create.CreateCardsRequest
import com.whatever.caro.card.internal.card.dto.create.CreateCardsResponse
import com.whatever.caro.card.internal.card.dto.create.toDto
import com.whatever.caro.card.internal.card.dto.create.toResponse
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardResponse
import com.whatever.caro.card.internal.card.dto.delete.toResponse
import com.whatever.caro.card.internal.card.dto.read.CardResponse
import com.whatever.caro.card.internal.card.dto.read.toResponse
import com.whatever.caro.card.internal.card.dto.update.UpdateCardRequest
import com.whatever.caro.card.internal.card.dto.update.UpdateCardResponse
import com.whatever.caro.card.internal.card.dto.update.toDto
import com.whatever.caro.card.internal.card.dto.update.toResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Parameter
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
class CardController(
    private val cardService: CardService,
) {
    @PostMapping("/v1/decks/{deckId}/cards")
    fun createCards(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
        @Valid @RequestBody request: CreateCardsRequest,
    ): ResponseEntity<ApiResponse<CreateCardsResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.createCards(userId = userId, dto = request.toDto(deckId)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/v1/decks/{deckId}/cards")
    fun getCardsByDeck(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<List<CardResponse>>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.getCardsByDeck(userId = userId, deckId = deckId).map { it.toResponse() }
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @GetMapping("/v1/cards/{id}")
    fun getCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<CardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.getCard(userId = userId, cardId = id).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @PatchMapping("/v1/cards/{id}")
    fun updateCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCardRequest,
    ): ResponseEntity<ApiResponse<UpdateCardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.updateCard(userId = userId, dto = request.toDto(cardId = id)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @DeleteMapping("/v1/cards/{id}")
    fun deleteCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<DeleteCardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.deleteCard(userId = userId, dto = DeleteCardDto(cardId = id)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
