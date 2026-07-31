package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.card.dto.create.CreateCardsRequest
import com.whatever.caro.card.internal.card.dto.create.CreateCardsResponse
import com.whatever.caro.card.internal.card.dto.create.toDto
import com.whatever.caro.card.internal.card.dto.create.toResponse
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardResponse
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardsRequest
import com.whatever.caro.card.internal.card.dto.delete.toResponse
import com.whatever.caro.card.internal.card.dto.read.CardResponse
import com.whatever.caro.card.internal.card.dto.read.toResponse
import com.whatever.caro.card.internal.card.dto.update.UpdateCardRequest
import com.whatever.caro.card.internal.card.dto.update.UpdateCardResponse
import com.whatever.caro.card.internal.card.dto.update.toDto
import com.whatever.caro.card.internal.card.dto.update.toResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId

@Tag(name = "Card", description = "카드 관리")
@RestController
class CardController(
    private val cardService: CardService,
) {
    @Operation(
        summary = "카드 생성",
        description = "덱에 여러 카드를 한 번에 생성한다.",
    )
    @PostMapping("/decks/{deckId}/cards", version = "1.0")
    fun createCards(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
        @Valid @RequestBody request: CreateCardsRequest,
    ): ResponseEntity<ApiResponse<CreateCardsResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.createCards(userId = userId, dto = request.toDto(deckId)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @Operation(
        summary = "덱의 카드 목록 조회 (deprecated)",
        description = "덱에 속한 카드 목록을 조회한다. 학습 상태(badge/복습 수)가 포함된 `GET /decks/{deckId}/cards` 로 대체되었다.",
        deprecated = true,
    )
    @Deprecated(message = "v2 메서드로 변경 필요")
    @GetMapping("/decks/{deckId}/cards", version = "1.0")
    fun getCardsByDeck(
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<List<CardResponse>>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.getCardsByDeck(userId = userId, deckId = deckId).map { it.toResponse() }
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @Operation(
        summary = "카드 단건 조회",
        description = "카드 ID로 카드 1건을 조회한다.",
    )
    @GetMapping("/cards/{id}", version = "1.0")
    fun getCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
    ): ResponseEntity<ApiResponse<CardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.getCard(userId = userId, cardId = id).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @Operation(
        summary = "카드 수정",
        description = "카드의 필드 값을 수정한다.",
    )
    @PatchMapping("/cards/{id}", version = "1.0")
    fun updateCard(
        @Parameter(description = "카드 ID", required = true)
        @Positive @PathVariable id: Long,
        @Valid @RequestBody request: UpdateCardRequest,
    ): ResponseEntity<ApiResponse<UpdateCardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.updateCard(userId = userId, dto = request.toDto(cardId = id)).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }

    @Operation(
        summary = "카드 삭제",
        description = """
        카드를 삭제(soft delete)한다.
        삭제는 일일학습 목표/진행도와 streak 계산에 반영되므로 `Client-Timezone` 헤더로 사용자의 오늘 경계를 판단한다.
        """,
    )
    @DeleteMapping("/cards", version = "1.0")
    fun deleteCards(
        @RequestHeader("Client-Timezone", required = true)
        timezone: ZoneId,
        @RequestBody @Valid cards: DeleteCardsRequest,
    ): ResponseEntity<ApiResponse<DeleteCardResponse>> {
        val userId = SecurityUtil.currentUser().userId
        val result = cardService.deleteCard(
            userId = userId,
            timezone = timezone,
            dto = DeleteCardDto(cardIds = cards.cardIds),
        ).toResponse()
        return ResponseEntity.ok(ApiResponse.ok(result))
    }
}
