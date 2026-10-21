package com.whatever.caro.study.internal.report

import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// JPQL 생성자 표현식의 결과. 행이 없으면 sum은 null이다.

class ReportCountsRow(
    val reviewCount: Long,
    val uniqueCardCount: Long,
    val studyDayCount: Long,
    val easy: Long?,
    val fair: Long?,
    val again: Long?,
)

class ReportDailyRow(
    val studyDate: LocalDate,
    val reviewCount: Long,
    val easy: Long?,
    val fair: Long?,
    val again: Long?,
)

class ReportSessionRow(
    val sessionId: Long,
    val deckId: Long,
    val studyDate: LocalDate,
    val timezone: ZoneId,
    val status: StudySessionStatus,
    val reviewCount: Long,
    val uniqueCardCount: Long,
    val easy: Long?,
    val fair: Long?,
    val again: Long?,
)

class ReportCardLastRow(
    val cardId: Long,
    val reviewId: Long,
    val rating: Rating,
    val reviewedAt: Instant,
    val studyDate: LocalDate,
)

class ReportCardCountRow(
    val cardId: Long,
    val reviewCount: Long,
)

class ReportReviewRow(
    val reviewId: Long,
    val sessionId: Long,
    val rating: Rating,
    val studyDate: LocalDate,
    val reviewedAt: Instant,
    val timezone: ZoneId,
    val reviewType: ReviewType,
)
