package com.whatever.caro.bff.internal

import com.whatever.caro.study.StudyReportDailyCountDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 학습량 차트의 구간을 만든다. 빈 구간 없이 범위 전체를 덮고, 평가가 없는 구간은 0이다.
 * 31일 이하는 일, 180일 이하는 주(시작일부터 7일씩), 그보다 길면 달력의 월로 나누고 첫·마지막 구간은 범위에 맞춰 자른다.
 */
object ReportBuckets {
    private const val MAX_DAY_BUCKET_DAYS = 31L
    private const val MAX_WEEK_BUCKET_DAYS = 180L
    private const val DAYS_PER_WEEK = 7L

    fun unitOf(
        startDate: LocalDate,
        endDate: LocalDate,
    ): ReportBucketUnit {
        val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
        return when {
            days <= MAX_DAY_BUCKET_DAYS -> ReportBucketUnit.DAY
            days <= MAX_WEEK_BUCKET_DAYS -> ReportBucketUnit.WEEK
            else -> ReportBucketUnit.MONTH
        }
    }

    fun build(
        startDate: LocalDate,
        endDate: LocalDate,
        unit: ReportBucketUnit,
        dailyCounts: List<StudyReportDailyCountDto>,
    ): List<ReportBucket> {
        val buckets = mutableListOf<ReportBucket>()
        var from = startDate
        while (!from.isAfter(endDate)) {
            val to = minOf(bucketEnd(from, unit), endDate)
            val inRange = dailyCounts.filter { !it.studyDate.isBefore(from) && !it.studyDate.isAfter(to) }
            buckets += ReportBucket(
                startDate = from,
                endDate = to,
                reviewCount = inRange.sumOf { it.reviewCount },
                easy = inRange.sumOf { it.ratings.easy },
                fair = inRange.sumOf { it.ratings.fair },
                again = inRange.sumOf { it.ratings.again },
            )
            from = to.plusDays(1)
        }
        return buckets
    }

    private fun bucketEnd(
        bucketStart: LocalDate,
        unit: ReportBucketUnit,
    ): LocalDate =
        when (unit) {
            ReportBucketUnit.DAY -> bucketStart
            ReportBucketUnit.WEEK -> bucketStart.plusDays(DAYS_PER_WEEK - 1)
            ReportBucketUnit.MONTH -> bucketStart.with(TemporalAdjusters.lastDayOfMonth())
        }
}
