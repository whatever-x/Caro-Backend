package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.card.internal.card.dto.create.CreateCardChunkRequest
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
import com.whatever.caro.common.web.idempotency.Idempotent
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
        description = """
        덱에 여러 카드를 한 번에 생성한다.
        새로 연동하는 클라이언트는 중복 저장 방지 키를 받는 1.1을 사용한다. 1.0은 기존 앱과 수동 카드 생성을 위해 유지한다.
        """,
    )
    @Deprecated(message = "1.1이 이 계약을 잇는다. 수동 카드 생성을 1.1로 옮기고 기존 앱 지원이 끝나면 제거할 수 있다")
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

    // Mobile swagger-sync가 이 문서를 operationId로 대조하므로, 값이 바뀌면 1.0 createCards가 삭제된 엔드포인트로 처리된다.
    // Mobile이 버전별 API 문서를 각각 읽어 호출 코드를 관리하게 되면 이 operationId 지정을 지운다.
    @Operation(
        operationId = "createCards",
        summary = "카드 묶음 생성",
        description = """
        외부 학습 데이터 가져오기처럼 많은 카드를 여러 요청으로 나눠 저장한다. 요청·응답 형식은 1.0과 같다.
        한 요청의 `items`는 1~${CreateCardChunkRequest.MAX_ITEMS}개이며, 한 요청의 카드는 모두 저장되거나 모두 저장되지 않는다.
        같은 덱의 묶음은 이전 묶음의 응답을 받은 뒤 순서대로 보낸다. 그래야 cardId 오름차순이 요청 순서와 같다.

        중복 저장 방지를 위해 묶음마다 새 UUID를 `Idempotency-Key` 헤더로 보내야 한다.
        재시도할 때는 같은 키와 같은 본문 바이트를 보내며, 같은 키의 요청이 처리 중이면 409(C010)를 받는다.
        """,
    )
    @Idempotent
    @PostMapping("/decks/{deckId}/cards", version = "1.1")
    fun createCardChunk(
        @RequestHeader("Idempotency-Key", required = true) idempotencyKey: String,
        @Parameter(description = "덱 ID", required = true)
        @Positive @PathVariable deckId: Long,
        @Valid @RequestBody request: CreateCardChunkRequest,
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
