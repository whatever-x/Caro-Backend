package com.whatever.caro.bff.internal

import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudyReportCountsDto
import com.whatever.caro.study.StudySessionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class ReportBucketUnit { DAY, WEEK, MONTH }

/**
 * 조회한 덱. 전체 선택은 deckId·deckName이 null이고, 삭제된 덱은 이름 없이 deleted=true다.
 */
data class ReportScope(
    val deckId: Long?,
    val deckName: String?,
    val deleted: Boolean,
)

/**
 * 실제로 조회한 날짜 범위. 전체 기간에 기록이 없을 때만 startDate가 null이다.
 */
data class ReportRange(
    val startDate: LocalDate?,
    val endDate: LocalDate,
    val bucketUnit: ReportBucketUnit,
)

data class ReportBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reviewCount: Long,
    val easy: Long,
    val fair: Long,
    val again: Long,
)

data class ReportSummaryView(
    val scope: ReportScope,
    val range: ReportRange,
    val lifetime: StudyReportCountsDto?,
    val selected: StudyReportCountsDto,
    val buckets: List<ReportBucket>,
    val restDates: List<LocalDate>,
)

data class ReportPageView<T>(
    val items: List<T>,
    val nextCursor: String?,
)

data class ReportDeckOption(
    val deckId: Long,
    val name: String?,
    val deleted: Boolean,
)

data class ReportSessionItem(
    val sessionId: Long,
    val deckId: Long,
    val deckName: String?,
    val deckDeleted: Boolean,
    val studyDate: LocalDate,
    val timezone: ZoneId,
    val status: StudySessionStatus,
    val counts: StudyReportCountsDto,
)

/**
 * 카드의 지금 내용. 삭제된 카드와 삭제된 덱의 카드는 본문 없이 deleted=true다.
 */
data class ReportCardLabel(
    val cardId: Long,
    val deleted: Boolean,
    val front: String?,
    val back: String?,
)

data class ReportCardItem(
    val card: ReportCardLabel,
    val reviewCount: Long,
    val lastRating: Rating,
    val lastReviewedAt: Instant,
    val lastStudyDate: LocalDate,
)

data class ReportReviewItem(
    val reviewId: Long,
    val sessionId: Long,
    val rating: Rating,
    val studyDate: LocalDate,
    val reviewedAt: Instant,
    val timezone: ZoneId,
    val reviewType: ReviewType,
)

data class ReportCardReviewsView(
    val card: ReportCardLabel,
    val range: ReportRange,
    val page: ReportPageView<ReportReviewItem>,
)
