package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.api.deck.DeckInfoResponse
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudyReportApi
import com.whatever.caro.study.StudyReportCardDto
import com.whatever.caro.study.StudyReportCountsDto
import com.whatever.caro.study.StudyReportDailyCountDto
import com.whatever.caro.study.StudyReportPeriod
import com.whatever.caro.study.StudyReportRatingCountsDto
import com.whatever.caro.study.StudyReportReviewDto
import com.whatever.caro.study.StudyReportSessionDto
import com.whatever.caro.study.StudyReportSessionKey
import com.whatever.caro.study.StudySessionStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StudyReportBFFServiceUnitTest :
    DescribeSpec({
        val studyReportApi = mockk<StudyReportApi>()
        val deckApi = mockk<DeckApi>()
        val cardApi = mockk<CardApi>()
        val service = StudyReportBFFService(studyReportApi = studyReportApi, deckApi = deckApi, cardApi = cardApi)

        val userId = 1L
        val kst = ZoneId.of("Asia/Seoul")
        // KST 2026-10-21 10:00
        val now = Instant.parse("2026-10-21T01:00:00Z")
        val today = LocalDate.parse("2026-10-21")
        val weekStart = LocalDate.parse("2026-10-15")
        val max = 500L

        fun counts(
            review: Long,
            easy: Long = review,
        ) = StudyReportCountsDto(
            reviewCount = review,
            uniqueCardCount = review,
            studyDayCount = if (review > 0) 1 else 0,
            ratings = StudyReportRatingCountsDto(easy = easy, fair = review - easy, again = 0),
        )

        fun deck(
            id: Long,
            name: String = "덱$id",
        ) = DeckInfoResponse(id = id, userId = userId, name = name, description = "", cardCount = 0)

        fun daily(
            date: LocalDate,
            review: Long,
        ) = StudyReportDailyCountDto(date, review, StudyReportRatingCountsDto(review, 0, 0))

        beforeTest {
            every { studyReportApi.getMaxReviewId(userId) } returns max
            every { deckApi.getDecks(userId) } returns listOf(deck(1L, "영어 단어"))
            every { studyReportApi.getHistoryDeckIds(userId) } returns setOf(1L, 9L)
        }

        afterTest { clearMocks(studyReportApi, deckApi, cardApi) }

        describe("getSummary") {
            it("전체를 고르면 전체 누적과 휴식일을 함께 주고 빈 날짜도 0인 구간으로 채운다") {
                val period = StudyReportPeriod(weekStart, today)
                every { studyReportApi.getCounts(userId, null, period, max) } returns counts(5)
                every { studyReportApi.getCounts(userId, null, null, max) } returns counts(40)
                every { studyReportApi.getDailyCounts(userId, null, period, max) } returns
                    listOf(daily(weekStart, 2), daily(today, 3))
                every { studyReportApi.getRestDates(userId, period) } returns
                    setOf(LocalDate.parse("2026-10-17"), today)

                val summary = service.getSummary(now, kst, userId, null, weekStart, today)

                summary.scope shouldBe ReportScope(null, null, false)
                summary.lifetime?.reviewCount shouldBe 40
                summary.selected.reviewCount shouldBe 5
                summary.range shouldBe ReportRange(weekStart, today, ReportBucketUnit.DAY)
                summary.buckets.size shouldBe 7
                summary.buckets.sumOf { it.reviewCount } shouldBe summary.selected.reviewCount
                summary.buckets[1].reviewCount shouldBe 0
                // 평가가 있는 날은 휴식일로 표시하지 않는다
                summary.restDates shouldContainExactly listOf(LocalDate.parse("2026-10-17"))
            }

            it("덱을 고르면 전체 누적과 휴식일을 주지 않는다") {
                val period = StudyReportPeriod(weekStart, today)
                every { studyReportApi.getCounts(userId, 1L, period, max) } returns counts(3)
                every { studyReportApi.getDailyCounts(userId, 1L, period, max) } returns emptyList()

                val summary = service.getSummary(now, kst, userId, 1L, weekStart, today)

                summary.scope shouldBe ReportScope(1L, "영어 단어", false)
                summary.lifetime.shouldBeNull()
                summary.restDates.shouldBeEmpty()
                verify(exactly = 0) { studyReportApi.getCounts(userId, null, null, any()) }
                verify(exactly = 0) { studyReportApi.getRestDates(any(), any()) }
            }

            it("삭제된 덱은 이름 없이 deleted로 조회한다") {
                every { studyReportApi.getCounts(userId, 9L, any(), max) } returns counts(1)
                every { studyReportApi.getDailyCounts(userId, 9L, any(), max) } returns emptyList()

                service.getSummary(now, kst, userId, 9L, weekStart, today).scope shouldBe ReportScope(9L, null, true)
            }

            it("가진 덱도 아니고 기록도 없는 덱이면 404 예외를 던진다") {
                shouldThrow<ReportTargetNotFoundException> {
                    service.getSummary(now, kst, userId, 77L, weekStart, today)
                }
            }

            it("전체 기간은 첫 학습일부터 오늘까지이며 기록이 없으면 시작일이 null이다") {
                every { studyReportApi.getCounts(userId, null, null, max) } returns counts(0)
                every { studyReportApi.getFirstStudyDate(userId, null, max) } returns null

                val summary = service.getSummary(now, kst, userId, null, null, null)

                summary.range shouldBe ReportRange(null, today, ReportBucketUnit.DAY)
                summary.buckets.shouldBeEmpty()
            }

            it("전체 기간이 길면 월 단위 구간으로 나눈다") {
                val first = LocalDate.parse("2026-01-10")
                every { studyReportApi.getCounts(userId, 1L, null, max) } returns counts(9)
                every { studyReportApi.getFirstStudyDate(userId, 1L, max) } returns first
                every { studyReportApi.getDailyCounts(userId, 1L, StudyReportPeriod(first, today), max) } returns
                    listOf(daily(first, 4), daily(LocalDate.parse("2026-10-01"), 5))

                val summary = service.getSummary(now, kst, userId, 1L, null, null)

                summary.range.bucketUnit shouldBe ReportBucketUnit.MONTH
                summary.buckets.first().startDate shouldBe first
                summary.buckets.last().endDate shouldBe today
                summary.buckets.sumOf { it.reviewCount } shouldBe 9
            }

            describe("기간 검증") {
                it("한쪽 날짜만 보내면 예외를 던진다") {
                    shouldThrow<InvalidReportPeriodException> { service.getSummary(now, kst, userId, null, weekStart, null) }
                }

                it("시작일이 종료일보다 늦으면 예외를 던진다") {
                    shouldThrow<InvalidReportPeriodException> { service.getSummary(now, kst, userId, null, today, weekStart) }
                }

                it("종료일이 사용자 시간대의 오늘보다 늦으면 예외를 던진다") {
                    shouldThrow<InvalidReportPeriodException> {
                        service.getSummary(now, kst, userId, null, weekStart, today.plusDays(1))
                    }
                }

                it("오늘은 사용자 시간대로 정한다") {
                    // UTC로는 10-20이지만 KST로는 10-21이다
                    val utcEvening = Instant.parse("2026-10-20T16:00:00Z")
                    val period = StudyReportPeriod(today, today)
                    every { studyReportApi.getCounts(userId, null, any(), max) } returns counts(0)
                    every { studyReportApi.getDailyCounts(userId, null, period, max) } returns emptyList()
                    every { studyReportApi.getRestDates(userId, period) } returns emptySet()

                    service.getSummary(utcEvening, kst, userId, null, today, today).range.endDate shouldBe today
                }
            }
        }

        describe("getDeckOptions") {
            it("가진 덱과 기록이 남은 삭제 덱을 deckId 내림차순으로 나눠 준다") {
                every { deckApi.getDecks(userId) } returns listOf(deck(1L), deck(3L), deck(5L))
                every { studyReportApi.getHistoryDeckIds(userId) } returns setOf(1L, 4L)

                val first = service.getDeckOptions(userId, cursor = null, limit = 2)
                val second = service.getDeckOptions(userId, cursor = first.nextCursor, limit = 2)

                first.items shouldContainExactly listOf(
                    ReportDeckOption(5L, "덱5", false),
                    ReportDeckOption(4L, null, true),
                )
                second.items shouldContainExactly listOf(
                    ReportDeckOption(3L, "덱3", false),
                    ReportDeckOption(1L, "덱1", false),
                )
                second.nextCursor.shouldBeNull()
            }

            it("다른 목록의 커서는 거절한다") {
                every { studyReportApi.getSessions(userId, null, null, null, max, 2) } returns listOf(
                    session(1L, 1L),
                    session(2L, 1L),
                )
                val sessionCursor = service.getSessions(now, kst, userId, null, null, null, null, 1).nextCursor

                shouldThrow<InvalidReportCursorException> { service.getDeckOptions(userId, sessionCursor, 2) }
            }
        }

        describe("getSessions") {
            it("덱 이름을 붙이고 삭제된 덱은 이름 없이 표시한다") {
                every { studyReportApi.getSessions(userId, null, null, null, max, 21) } returns
                    listOf(session(10L, 1L), session(9L, 9L))

                val page = service.getSessions(now, kst, userId, null, null, null, null, 20)

                page.items.map { Triple(it.sessionId, it.deckName, it.deckDeleted) } shouldContainExactly listOf(
                    Triple(10L, "영어 단어", false),
                    Triple(9L, null, true),
                )
                page.nextCursor.shouldBeNull()
            }

            it("다음 페이지는 첫 페이지의 기준 시점과 마지막 위치로 이어서 읽는다") {
                every { studyReportApi.getSessions(userId, null, null, null, max, 2) } returns
                    listOf(session(10L, 1L), session(9L, 1L))
                val cursor = service.getSessions(now, kst, userId, null, null, null, null, 1).nextCursor.shouldNotBeNull()

                every { studyReportApi.getMaxReviewId(userId) } returns max + 100
                val after = StudyReportSessionKey(LocalDate.parse("2026-10-10"), 10L)
                every { studyReportApi.getSessions(userId, null, null, after, max, 2) } returns listOf(session(9L, 1L))

                service.getSessions(now, kst, userId, null, null, null, cursor, 1).items.map { it.sessionId } shouldContainExactly
                    listOf(9L)
            }

            it("조건이 바뀌면 이전 커서를 거절한다") {
                every { studyReportApi.getSessions(userId, null, null, null, max, 2) } returns
                    listOf(session(10L, 1L), session(9L, 1L))
                val cursor = service.getSessions(now, kst, userId, null, null, null, null, 1).nextCursor

                shouldThrow<InvalidReportCursorException> {
                    service.getSessions(now, kst, userId, 1L, null, null, cursor, 1)
                }
            }

            it("형식이 틀린 커서는 거절한다") {
                shouldThrow<InvalidReportCursorException> {
                    service.getSessions(now, kst, userId, null, null, null, "not-a-cursor!", 1)
                }
            }
        }

        describe("getCards") {
            it("지금 카드 내용을 붙이고 삭제된 카드는 본문 없이 표시한다") {
                every { studyReportApi.getCards(userId, 1L, null, null, null, max, 21) } returns
                    listOf(card(100L, 3L), card(101L, 2L))
                every { cardApi.getCardsByIds(userId, listOf(100L, 101L)) } returns
                    mapOf(100L to CardContentDto(100L, mapOf("front" to "apple", "back" to "사과")))

                val page = service.getCards(now, kst, userId, 1L, null, null, null, null, 20)

                page.items.map { it.card } shouldContainExactly listOf(
                    ReportCardLabel(100L, false, "apple", "사과"),
                    ReportCardLabel(101L, true, null, null),
                )
            }

            it("삭제된 덱의 카드는 카드가 남아 있어도 본문을 읽지 않고 숨긴다") {
                every { studyReportApi.getCards(userId, 9L, null, Rating.EASY, null, max, 21) } returns
                    listOf(card(200L, 1L))

                val page = service.getCards(now, kst, userId, 9L, null, null, Rating.EASY, null, 20)

                page.items.single().card shouldBe ReportCardLabel(200L, true, null, null)
                verify(exactly = 0) { cardApi.getCardsByIds(any(), any()) }
            }
        }

        describe("getCardReviews") {
            it("이 덱에서 평가한 적 없는 카드면 404 예외를 던진다") {
                every { studyReportApi.getCardReviews(userId, 1L, 100L, null, null, max, 1) } returns emptyList()

                shouldThrow<ReportTargetNotFoundException> {
                    service.getCardReviews(now, kst, userId, 1L, 100L, null, null, null, 20)
                }
            }

            it("카드 내용과 이력을 최신순으로 준다") {
                val review = review(300L)
                every { studyReportApi.getCardReviews(userId, 1L, 100L, null, null, max, 1) } returns listOf(review)
                every { studyReportApi.getCardReviews(userId, 1L, 100L, null, null, max, 21) } returns listOf(review)
                every { studyReportApi.getFirstStudyDate(userId, 1L, max) } returns weekStart
                every { cardApi.getCardsByIds(userId, listOf(100L)) } returns
                    mapOf(100L to CardContentDto(100L, mapOf("front" to "apple", "back" to "사과")))

                val view = service.getCardReviews(now, kst, userId, 1L, 100L, null, null, null, 20)

                view.card shouldBe ReportCardLabel(100L, false, "apple", "사과")
                view.range shouldBe ReportRange(weekStart, today, ReportBucketUnit.DAY)
                view.page.items.map { it.reviewId } shouldContainExactly listOf(300L)
            }
        }
    }) {
    companion object {
        fun session(
            sessionId: Long,
            deckId: Long,
        ) = StudyReportSessionDto(
            sessionId = sessionId,
            deckId = deckId,
            studyDate = LocalDate.parse("2026-10-10"),
            timezone = ZoneId.of("Asia/Seoul"),
            status = StudySessionStatus.COMPLETED,
            counts = StudyReportCountsDto(1, 1, 1, StudyReportRatingCountsDto(1, 0, 0)),
        )

        fun card(
            cardId: Long,
            reviewCount: Long,
        ) = StudyReportCardDto(
            cardId = cardId,
            reviewCount = reviewCount,
            lastReviewId = cardId * 10,
            lastRating = Rating.FAIR,
            lastReviewedAt = Instant.parse("2026-10-20T01:00:00Z"),
            lastStudyDate = LocalDate.parse("2026-10-20"),
        )

        fun review(
            reviewId: Long,
        ) = StudyReportReviewDto(
            reviewId = reviewId,
            sessionId = 1L,
            rating = Rating.AGAIN,
            studyDate = LocalDate.parse("2026-10-19"),
            reviewedAt = Instant.parse("2026-10-19T12:00:00Z"),
            timezone = ZoneId.of("Asia/Seoul"),
            reviewType = ReviewType.REVIEW,
        )
    }
}
