package com.whatever.caro.card.internal.deck.controller

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckRequest
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponse
import com.whatever.caro.card.internal.deck.dto.create.toDto
import com.whatever.caro.card.internal.deck.dto.create.toResponse
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckResponse
import com.whatever.caro.card.internal.deck.dto.delete.toResponse
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckRequest
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckResponse
import com.whatever.caro.card.internal.deck.dto.update.toDto
import com.whatever.caro.card.internal.deck.dto.update.toResponse
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Deck", description = "단어/표현 덱 관리")
@Validated
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

    @Operation(
        summary = "덱 삭제",
        description = "덱을 삭제한다. 덱에 속한 카드와 학습 상태도 함께 정리된다.",
    )
    @DeleteMapping("/{deckId}")
    fun deleteDeck(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<DeleteDeckResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val deck = deckService.deleteDeck(
            userId = userId,
            deleteDeckDto = DeleteDeckDto(deckId = deckId),
        ).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(deck))
    }

    @Operation(
        summary = "덱 수정",
        description = "덱의 이름/설명 등 메타데이터를 수정한다.",
    )
    @PatchMapping("/{deckId}")
    fun updateDeck(
        @Parameter(description = "덱 ID", required = true)
        @PathVariable deckId: Long,
        @Valid @RequestBody updateDeckRequest: UpdateDeckRequest,
    ): ResponseEntity<ApiResponse<UpdateDeckResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val deck = deckService.updateDeck(userId = userId, updateDeckDto = updateDeckRequest.toDto(deckId)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(deck))
    }
}
