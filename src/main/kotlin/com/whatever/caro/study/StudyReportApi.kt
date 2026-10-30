package com.whatever.caro.study

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 학습 리포트용 조회 API
 *
 * 일일 학습(DAILY) 세션에 저장된 평가 로그만 집계한다. 카드·덱 삭제와 무관하게 이력은 그대로 집계한다.
 * 모든 조회는 [maxReviewId] 이하의 로그만 읽어, 여러 번 나눠 호출해도 같은 시점의 결과를 만든다.
 */
interface StudyReportApi {
    /**
     * 현재 사용자의 마지막 평가 로그 id. 기록이 없으면 0
     */
    fun getMaxReviewId(
        userId: Long,
    ): Long

    /**
     * 평가 로그가 있는 덱 id 집합(삭제된 덱 포함)
     */
    fun getHistoryDeckIds(
        userId: Long,
    ): Set<Long>

    /**
     * 평가 로그가 있는 첫 학습일. 기록이 없으면 null
     */
    fun getFirstStudyDate(
        userId: Long,
        deckId: Long?,
        maxReviewId: Long,
    ): LocalDate?

    fun getCounts(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod?,
        maxReviewId: Long,
    ): StudyReportCountsDto

    /**
     * 평가가 있는 학습일별 집계. 평가가 없는 날은 포함하지 않는다.
     */
    fun getDailyCounts(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod,
        maxReviewId: Long,
    ): List<StudyReportDailyCountDto>

    /**
     * 기간 안에 휴식일로 기록된 날짜
     */
    fun getRestDates(
        userId: Long,
        period: StudyReportPeriod,
    ): Set<LocalDate>

    /**
     * 평가가 있는 세션을 (학습일, 세션 id) 내림차순으로 반환한다.
     */
    fun getSessions(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod?,
        after: StudyReportSessionKey?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportSessionDto>

    /**
     * 기간 안에 평가한 카드를 마지막 평가가 최신인 순서로 반환한다.
     * [lastRating]은 기간 안의 마지막 평가에 적용한다.
     */
    fun getCards(
        userId: Long,
        deckId: Long,
        period: StudyReportPeriod?,
        lastRating: Rating?,
        afterReviewId: Long?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportCardDto>

    /**
     * 카드 하나의 평가 이력을 최신순으로 반환한다.
     */
    fun getCardReviews(
        userId: Long,
        deckId: Long,
        cardId: Long,
        period: StudyReportPeriod?,
        afterReviewId: Long?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportReviewDto>
}

/**
 * 시작일과 종료일을 모두 포함하는 학습일 범위
 */
data class StudyReportPeriod(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!startDate.isAfter(endDate)) { "startDate($startDate) must not be after endDate($endDate)" }
    }
}

data class StudyReportRatingCountsDto(
    val easy: Long,
    val fair: Long,
    val again: Long,
)

data class StudyReportCountsDto(
    val reviewCount: Long,
    val uniqueCardCount: Long,
    val studyDayCount: Long,
    val ratings: StudyReportRatingCountsDto,
)

data class StudyReportDailyCountDto(
    val studyDate: LocalDate,
    val reviewCount: Long,
    val ratings: StudyReportRatingCountsDto,
)

data class StudyReportSessionKey(
    val studyDate: LocalDate,
    val sessionId: Long,
)

data class StudyReportSessionDto(
    val sessionId: Long,
    val deckId: Long,
    val studyDate: LocalDate,
    val timezone: ZoneId,
    val status: StudySessionStatus,
    val counts: StudyReportCountsDto,
)

data class StudyReportCardDto(
    val cardId: Long,
    val reviewCount: Long,
    val lastReviewId: Long,
    val lastRating: Rating,
    val lastReviewedAt: Instant,
    val lastStudyDate: LocalDate,
)

data class StudyReportReviewDto(
    val reviewId: Long,
    val sessionId: Long,
    val rating: Rating,
    val studyDate: LocalDate,
    val reviewedAt: Instant,
    val timezone: ZoneId,
    val reviewType: ReviewType,
)
