package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.bff.internal.DeckBFFService
import com.whatever.caro.bff.internal.DeckCardItem
import com.whatever.caro.bff.internal.web.response.DeckCardResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Deck Card Information", description = "Deck card information")
@RestController
class DeckBFFController(
    private val deckBFFService: DeckBFFService,
) {

    @Operation(
        summary = "덱에 있는 카드 조회",
        description = """
        덱에 있는 모든 카드를 조회한다.
        각 카드별로 학습 상태를 기반으로 NEW/REVIEW/HARD badge, 복습 수가 함께 제공된다.
        """,
    )
    @GetMapping("/v2/decks/{deckId}/cards")
    fun getCardsByDeck(
        @Parameter(description = "덱 ID", required = true) @PathVariable deckId: Long,
    ): ResponseEntity<ApiResponse<List<DeckCardResponse>>> {
        val userId = SecurityUtil.currentUser().userId
        val cards = deckBFFService.getCardsWithLearningState(
            userId = userId,
            deckId = deckId,
        )
        return ResponseEntity.ok(ApiResponse.ok(cards.map { it.toResponse() }))
    }
}

private fun DeckCardItem.toResponse(): DeckCardResponse =
    DeckCardResponse(
        cardId = cardId,
        fields = fields,
        badge = badge,
        reviewCount = reviewCount,
    )
