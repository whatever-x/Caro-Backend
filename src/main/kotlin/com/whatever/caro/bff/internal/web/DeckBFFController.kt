package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.bff.internal.DeckBFFService
import com.whatever.caro.bff.internal.DeckCardItem
import com.whatever.caro.bff.internal.DeckListItem
import com.whatever.caro.bff.internal.StudySessionProgress
import com.whatever.caro.bff.internal.web.response.DeckCardResponse
import com.whatever.caro.bff.internal.web.response.DeckListResponse
import com.whatever.caro.bff.internal.web.response.StudySessionProgressResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@Tag(name = "Deck Card Information", description = "Deck card information")
@RestController
class DeckBFFController(
    private val clock: Clock,
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

    @Operation(
        summary = "홈 화면 덱 목록 조회",
        description = """
        홈 화면에 표시될 덱 목록을 조회한다.
        각 덱마다 오늘의 일일학습 진행 정보(progress)가 포함된다.

        totalCardCount는 모든 상태에서 "오늘 학습 목표 장 수"를 의미한다.
        (NOT_STARTED는 오늘 학습 목표의 합, REST_DAY는 0)
        """,
    )
    @GetMapping("/v1/decks")
    fun getDecks(
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
    ): ResponseEntity<ApiResponse<List<DeckListResponse>>> {
        val now = Instant.now(clock)
        val userId = SecurityUtil.currentUser().userId

        val decks = deckBFFService.getDecksWithDailyStudySession(
            now = now,
            timezone = timezone,
            userId = userId,
        )
        return ResponseEntity.ok(ApiResponse.ok(decks.map { it.toResponse() }))
    }
}

private fun DeckCardItem.toResponse(): DeckCardResponse =
    DeckCardResponse(
        cardId = cardId,
        fields = fields,
        badge = badge,
        reviewCount = reviewCount,
    )

private fun DeckListItem.toResponse(): DeckListResponse =
    DeckListResponse(
        deckId = deckId,
        name = name,
        description = description,
        cardCount = cardCount,
        progress = progress.toResponse(),
    )

private fun StudySessionProgress.toResponse(): StudySessionProgressResponse =
    StudySessionProgressResponse(
        state = state,
        sessionId = sessionId,
        studiedCardCount = studiedCardCount,
        totalCardCount = totalCardCount,
    )
