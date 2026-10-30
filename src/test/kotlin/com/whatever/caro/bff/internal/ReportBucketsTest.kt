package com.whatever.caro.bff.internal

import com.whatever.caro.study.StudyReportDailyCountDto
import com.whatever.caro.study.StudyReportRatingCountsDto
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate

class ReportBucketsTest :
    DescribeSpec({
        fun d(
            value: String,
        ): LocalDate = LocalDate.parse(value)

        describe("unitOf") {
            it("31일 이하는 일, 180일 이하는 주, 그보다 길면 월이다") {
                ReportBuckets.unitOf(d("2026-10-01"), d("2026-10-31")) shouldBe ReportBucketUnit.DAY
                ReportBuckets.unitOf(d("2026-10-01"), d("2026-11-01")) shouldBe ReportBucketUnit.WEEK
                ReportBuckets.unitOf(d("2026-01-01"), d("2026-06-29")) shouldBe ReportBucketUnit.WEEK
                ReportBuckets.unitOf(d("2026-01-01"), d("2026-06-30")) shouldBe ReportBucketUnit.MONTH
            }
        }

        describe("build") {
            it("하루 범위는 구간 하나다") {
                ReportBuckets.build(d("2026-10-21"), d("2026-10-21"), ReportBucketUnit.DAY, emptyList())
                    .map { it.startDate to it.endDate } shouldContainExactly listOf(d("2026-10-21") to d("2026-10-21"))
            }

            it("주 구간은 시작일부터 7일씩 나누고 마지막 구간은 종료일에서 자른다") {
                val buckets = ReportBuckets.build(d("2026-10-01"), d("2026-10-16"), ReportBucketUnit.WEEK, emptyList())

                buckets.map { it.startDate to it.endDate } shouldContainExactly listOf(
                    d("2026-10-01") to d("2026-10-07"),
                    d("2026-10-08") to d("2026-10-14"),
                    d("2026-10-15") to d("2026-10-16"),
                )
            }

            it("월 구간은 달력의 월로 나누고 첫 구간과 마지막 구간은 범위로 자른다") {
                val daily = listOf(
                    StudyReportDailyCountDto(d("2026-01-31"), 2, StudyReportRatingCountsDto(1, 1, 0)),
                    StudyReportDailyCountDto(d("2026-02-28"), 3, StudyReportRatingCountsDto(0, 0, 3)),
                    StudyReportDailyCountDto(d("2028-02-29"), 1, StudyReportRatingCountsDto(1, 0, 0)),
                )

                val buckets = ReportBuckets.build(d("2026-01-15"), d("2028-03-10"), ReportBucketUnit.MONTH, daily)

                buckets.first().let { (it.startDate to it.endDate) shouldBe (d("2026-01-15") to d("2026-01-31")) }
                buckets.first().reviewCount shouldBe 2
                buckets[1].let { (it.startDate to it.endDate) shouldBe (d("2026-02-01") to d("2026-02-28")) }
                buckets[1].again shouldBe 3
                buckets.first { it.startDate == d("2028-02-01") }.endDate shouldBe d("2028-02-29")
                buckets.last().let { (it.startDate to it.endDate) shouldBe (d("2028-03-01") to d("2028-03-10")) }
                buckets.sumOf { it.reviewCount } shouldBe 6
                buckets.size shouldBe 27
            }
        }
    })
