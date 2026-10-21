package com.whatever.caro.bff.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.bff.internal.ReportBucket
import com.whatever.caro.bff.internal.ReportCardItem
import com.whatever.caro.bff.internal.ReportCardLabel
import com.whatever.caro.bff.internal.ReportCardReviewsView
import com.whatever.caro.bff.internal.ReportDeckOption
import com.whatever.caro.bff.internal.ReportPageView
import com.whatever.caro.bff.internal.ReportRange
import com.whatever.caro.bff.internal.ReportReviewItem
import com.whatever.caro.bff.internal.ReportScope
import com.whatever.caro.bff.internal.ReportSessionItem
import com.whatever.caro.bff.internal.ReportSummaryView
import com.whatever.caro.bff.internal.StudyReportBFFService
import com.whatever.caro.bff.internal.web.response.ReportBucketResponse
import com.whatever.caro.bff.internal.web.response.ReportCardLabelResponse
import com.whatever.caro.bff.internal.web.response.ReportCardResponse
import com.whatever.caro.bff.internal.web.response.ReportCardReviewsResponse
import com.whatever.caro.bff.internal.web.response.ReportCountsResponse
import com.whatever.caro.bff.internal.web.response.ReportDeckOptionResponse
import com.whatever.caro.bff.internal.web.response.ReportPageResponse
import com.whatever.caro.bff.internal.web.response.ReportRangeResponse
import com.whatever.caro.bff.internal.web.response.ReportRatingsResponse
import com.whatever.caro.bff.internal.web.response.ReportReviewResponse
import com.whatever.caro.bff.internal.web.response.ReportScopeResponse
import com.whatever.caro.bff.internal.web.response.ReportSessionResponse
import com.whatever.caro.bff.internal.web.response.StudyReportSummaryResponse
import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.study.Rating
import com.whatever.caro.study.StudyReportCountsDto
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PERIOD_DESCRIPTION = """
        startDate와 endDate는 둘 다 보내거나 둘 다 생략한다. 생략하면 전체 기간이다.
        두 날짜를 포함하며, 한쪽만 보내거나 시작일이 종료일보다 늦거나 종료일이 Client-Timezone 기준 오늘보다 늦으면 400(R001)이다.
        날짜는 학습한 날(세션의 학습일) 기준이다. 일일 학습에 저장된 평가만 집계하고 벼락치기는 포함하지 않는다.
"""

@Tag(name = "StudyReport", description = "기간별 학습 리포트")
@RestController
@RequestMapping("/study-reports")
class StudyReportBFFController(
    private val clock: Clock,
    private val studyReportBFFService: StudyReportBFFService,
) {
    @Operation(
        summary = "학습 리포트 요약 조회",
        description = """
        선택한 덱(생략하면 전체)과 기간의 평가 횟수, 학습한 카드 수, 학습일 수, 평가 분포와 차트 구간을 조회한다.
        차트 구간은 31일 이하 DAY, 180일 이하 WEEK, 그보다 길면 MONTH다.
        지금 가진 덱이 아니고 본인 기록도 없는 deckId는 404(R003)다.
        $PERIOD_DESCRIPTION
        """,
    )
    @GetMapping("/summary", version = "1.0")
    fun getSummary(
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
        @Parameter(description = "덱 ID. 생략하면 전체") @RequestParam(required = false) deckId: Long?,
        @Parameter(description = "시작일(포함)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @Parameter(description = "종료일(포함)")
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
    ): ResponseEntity<ApiResponse<StudyReportSummaryResponse>> {
        val summary = studyReportBFFService.getSummary(
            now = Instant.now(clock),
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
            startDate = startDate,
            endDate = endDate,
        )
        return ResponseEntity.ok(ApiResponse.ok(summary.toResponse()))
    }

    @Operation(
        summary = "학습 리포트 덱 목록 조회",
        description = """
        조회 조건에서 고를 덱을 deckId 내림차순으로 조회한다.
        지금 가진 덱과, 삭제했지만 본인 평가 기록이 남은 덱(name null, deleted true)을 함께 준다.
        목록 맨 위의 "전체"는 응답에 없으며 클라이언트가 넣는다.
        """,
    )
    @GetMapping("/decks", version = "1.0")
    fun getDecks(
        @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (1~100, 기본 20)")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<ApiResponse<ReportPageResponse<ReportDeckOptionResponse>>> {
        val page = studyReportBFFService.getDeckOptions(
            userId = SecurityUtil.currentUser().userId,
            cursor = cursor,
            limit = limit,
        )
        return ResponseEntity.ok(ApiResponse.ok(page.toResponse { it.toResponse() }))
    }

    @Operation(
        summary = "학습 기록 목록 조회",
        description = """
        평가가 있는 일일 학습 세션을 (학습일, 세션 ID) 내림차순으로 조회한다. 중단한 세션도 저장된 평가가 있으면 포함한다.
        nextCursor는 첫 페이지와 같은 조건에서만 쓸 수 있고, 다른 조건이면 400(R002)이다.
        첫 페이지 이후에 저장된 평가는 같은 목록의 다음 페이지에 나오지 않는다.
        $PERIOD_DESCRIPTION
        """,
    )
    @GetMapping("/sessions", version = "1.0")
    fun getSessions(
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
        @Parameter(description = "덱 ID. 생략하면 전체") @RequestParam(required = false) deckId: Long?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (1~100, 기본 20)")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<ApiResponse<ReportPageResponse<ReportSessionResponse>>> {
        val page = studyReportBFFService.getSessions(
            now = Instant.now(clock),
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
            startDate = startDate,
            endDate = endDate,
            cursor = cursor,
            limit = limit,
        )
        return ResponseEntity.ok(ApiResponse.ok(page.toResponse { it.toResponse() }))
    }

    @Operation(
        summary = "카드별 평가 목록 조회",
        description = """
        덱에서 기간 안에 평가한 카드를 마지막 평가가 최신인 순서로 조회한다.
        lastRating은 기간 안의 마지막 평가에 적용한다.
        카드 본문은 지금 내용이다. 카드나 덱이 삭제됐으면 deleted true이고 front, back은 null이다.
        $PERIOD_DESCRIPTION
        """,
    )
    @GetMapping("/cards", version = "1.0")
    fun getCards(
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
        @Parameter(description = "덱 ID", required = true) @RequestParam deckId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @Parameter(description = "기간 안의 마지막 평가") @RequestParam(required = false) lastRating: Rating?,
        @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (1~100, 기본 20)")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<ApiResponse<ReportPageResponse<ReportCardResponse>>> {
        val page = studyReportBFFService.getCards(
            now = Instant.now(clock),
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
            startDate = startDate,
            endDate = endDate,
            lastRating = lastRating,
            cursor = cursor,
            limit = limit,
        )
        return ResponseEntity.ok(ApiResponse.ok(page.toResponse { it.toResponse() }))
    }

    @Operation(
        summary = "카드 평가 이력 조회",
        description = """
        덱에서 카드 하나의 평가 이력을 최신순으로 조회한다.
        이 덱에서 본인이 평가한 적 없는 카드는 404(R003)다.
        $PERIOD_DESCRIPTION
        """,
    )
    @GetMapping("/cards/{cardId}/reviews", version = "1.0")
    fun getCardReviews(
        @RequestHeader("Client-Timezone", required = true) timezone: ZoneId,
        @Parameter(description = "카드 ID", required = true) @PathVariable cardId: Long,
        @Parameter(description = "덱 ID", required = true) @RequestParam deckId: Long,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?,
        @Parameter(description = "이전 응답의 nextCursor") @RequestParam(required = false) cursor: String?,
        @Parameter(description = "페이지 크기 (1~100, 기본 20)")
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
    ): ResponseEntity<ApiResponse<ReportCardReviewsResponse>> {
        val view = studyReportBFFService.getCardReviews(
            now = Instant.now(clock),
            timezone = timezone,
            userId = SecurityUtil.currentUser().userId,
            deckId = deckId,
            cardId = cardId,
            startDate = startDate,
            endDate = endDate,
            cursor = cursor,
            limit = limit,
        )
        return ResponseEntity.ok(ApiResponse.ok(view.toResponse()))
    }
}

private fun ReportSummaryView.toResponse(): StudyReportSummaryResponse =
    StudyReportSummaryResponse(
        scope = scope.toResponse(),
        range = range.toResponse(),
        selected = selected.toResponse(),
        buckets = buckets.map { it.toResponse() },
        restDates = restDates,
    )

private fun ReportScope.toResponse(): ReportScopeResponse =
    ReportScopeResponse(deckId = deckId, deckName = deckName, deleted = deleted)

private fun ReportRange.toResponse(): ReportRangeResponse =
    ReportRangeResponse(startDate = startDate, endDate = endDate, bucketUnit = bucketUnit)

private fun StudyReportCountsDto.toResponse(): ReportCountsResponse =
    ReportCountsResponse(
        reviewCount = reviewCount,
        uniqueCardCount = uniqueCardCount,
        studyDayCount = studyDayCount,
        ratings = ReportRatingsResponse(easy = ratings.easy, fair = ratings.fair, again = ratings.again),
    )

private fun ReportBucket.toResponse(): ReportBucketResponse =
    ReportBucketResponse(
        startDate = startDate,
        endDate = endDate,
        reviewCount = reviewCount,
        ratings = ReportRatingsResponse(easy = easy, fair = fair, again = again),
    )

private fun <T, R> ReportPageView<T>.toResponse(
    transform: (T) -> R,
): ReportPageResponse<R> = ReportPageResponse(items = items.map(transform), nextCursor = nextCursor)

private fun ReportDeckOption.toResponse(): ReportDeckOptionResponse =
    ReportDeckOptionResponse(deckId = deckId, name = name, deleted = deleted)

private fun ReportSessionItem.toResponse(): ReportSessionResponse =
    ReportSessionResponse(
        sessionId = sessionId,
        deckId = deckId,
        deckName = deckName,
        deckDeleted = deckDeleted,
        studyDate = studyDate,
        timezone = timezone.id,
        status = status,
        counts = counts.toResponse(),
    )

private fun ReportCardItem.toResponse(): ReportCardResponse =
    ReportCardResponse(
        cardId = card.cardId,
        deleted = card.deleted,
        front = card.front,
        back = card.back,
        reviewCount = reviewCount,
        lastRating = lastRating,
        lastReviewedAt = lastReviewedAt,
        lastStudyDate = lastStudyDate,
    )

private fun ReportCardLabel.toResponse(): ReportCardLabelResponse =
    ReportCardLabelResponse(cardId = cardId, deleted = deleted, front = front, back = back)

private fun ReportReviewItem.toResponse(): ReportReviewResponse =
    ReportReviewResponse(
        reviewId = reviewId,
        sessionId = sessionId,
        rating = rating,
        studyDate = studyDate,
        reviewedAt = reviewedAt,
        timezone = timezone.id,
        reviewType = reviewType,
    )

private fun ReportCardReviewsView.toResponse(): ReportCardReviewsResponse =
    ReportCardReviewsResponse(
        card = card.toResponse(),
        range = range.toResponse(),
        items = page.items.map { it.toResponse() },
        nextCursor = page.nextCursor,
    )
