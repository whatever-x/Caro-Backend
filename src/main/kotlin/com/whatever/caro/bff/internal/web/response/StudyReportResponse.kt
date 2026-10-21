package com.whatever.caro.bff.internal.web.response

import com.whatever.caro.bff.internal.ReportBucketUnit
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

data class StudyReportSummaryResponse(
    val scope: ReportScopeResponse,
    val range: ReportRangeResponse,
    val selected: ReportCountsResponse,
    @field:Schema(description = "범위 전체를 빈칸 없이 덮는 차트 구간. 평가가 없는 구간은 0")
    val buckets: List<ReportBucketResponse>,
    @field:Schema(description = "\"전체\"의 일 단위 차트에서 평가 없이 휴식일로 기록된 날짜")
    val restDates: List<LocalDate>,
)

data class ReportScopeResponse(
    @field:Schema(description = "전체 선택이면 null", nullable = true)
    val deckId: Long?,
    @field:Schema(description = "전체 선택이거나 삭제된 덱이면 null", nullable = true)
    val deckName: String?,
    val deleted: Boolean,
)

data class ReportRangeResponse(
    @field:Schema(description = "전체 기간에 기록이 없을 때만 null", nullable = true)
    val startDate: LocalDate?,
    val endDate: LocalDate,
    val bucketUnit: ReportBucketUnit,
)

data class ReportCountsResponse(
    val reviewCount: Long,
    val uniqueCardCount: Long,
    val studyDayCount: Long,
    val ratings: ReportRatingsResponse,
)

data class ReportRatingsResponse(
    val easy: Long,
    val fair: Long,
    val again: Long,
)

data class ReportBucketResponse(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reviewCount: Long,
    val ratings: ReportRatingsResponse,
)

data class ReportPageResponse<T>(
    val items: List<T>,
    @field:Schema(description = "다음 페이지가 없으면 null", nullable = true)
    val nextCursor: String?,
)

data class ReportDeckOptionResponse(
    val deckId: Long,
    @field:Schema(description = "삭제된 덱이면 null", nullable = true)
    val name: String?,
    val deleted: Boolean,
)

data class ReportSessionResponse(
    val sessionId: Long,
    val deckId: Long,
    @field:Schema(description = "삭제된 덱이면 null", nullable = true)
    val deckName: String?,
    val deckDeleted: Boolean,
    val studyDate: LocalDate,
    val timezone: String,
    val status: StudySessionStatus,
    val counts: ReportCountsResponse,
)

data class ReportCardResponse(
    val cardId: Long,
    @field:Schema(description = "카드나 카드가 속한 덱이 삭제됐으면 true이고 front, back은 null")
    val deleted: Boolean,
    @field:Schema(nullable = true)
    val front: String?,
    @field:Schema(nullable = true)
    val back: String?,
    val reviewCount: Long,
    @field:Schema(description = "기간 안의 마지막 평가")
    val lastRating: Rating,
    val lastReviewedAt: Instant,
    val lastStudyDate: LocalDate,
)

data class ReportCardLabelResponse(
    val cardId: Long,
    val deleted: Boolean,
    @field:Schema(nullable = true)
    val front: String?,
    @field:Schema(nullable = true)
    val back: String?,
)

data class ReportReviewResponse(
    val reviewId: Long,
    val sessionId: Long,
    val rating: Rating,
    val studyDate: LocalDate,
    val reviewedAt: Instant,
    val timezone: String,
    val reviewType: ReviewType,
)

data class ReportCardReviewsResponse(
    val card: ReportCardLabelResponse,
    val range: ReportRangeResponse,
    val items: List<ReportReviewResponse>,
    @field:Schema(description = "다음 페이지가 없으면 null", nullable = true)
    val nextCursor: String?,
)
