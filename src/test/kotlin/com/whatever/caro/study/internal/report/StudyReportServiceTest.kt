package com.whatever.caro.study.internal.report

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StreakType
import com.whatever.caro.study.StudyReportPeriod
import com.whatever.caro.study.StudyReportRatingCountsDto
import com.whatever.caro.study.StudyReportSessionKey
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import com.whatever.caro.study.internal.streak.StudyDay
import com.whatever.caro.study.internal.streak.StudyDayRepository
import com.whatever.caro.study.internal.studysession.ReviewLog
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class StudyReportServiceTest(
    private val studyReportService: StudyReportService,
    private val studySessionRepository: StudySessionRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val studyDayRepository: StudyDayRepository,
) : DescribeSpec({
    val kst = ZoneId.of("Asia/Seoul")
    val day1 = LocalDate.parse("2026-10-15")
    val day2 = LocalDate.parse("2026-10-16")
    val day3 = LocalDate.parse("2026-10-17")

    beforeTest {
        reviewLogRepository.deleteAllInBatch()
        studySessionRepository.deleteAllInBatch()
        studyDayRepository.deleteAllInBatch()
    }

    afterTest {
        reviewLogRepository.deleteAllInBatch()
        studySessionRepository.deleteAllInBatch()
        studyDayRepository.deleteAllInBatch()
    }

    fun session(
        date: LocalDate,
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        studyType: StudyType = StudyType.DAILY,
        status: StudySessionStatus = StudySessionStatus.COMPLETED,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = userId,
                deckId = deckId,
                status = status,
                studyType = studyType,
                // cutoff 0, 현지 정오에 시작해 sessionDate가 date로 고정된다
                startedAt = date.atTime(12, 0).atZone(kst).toInstant(),
                timezone = kst,
                dayCutoffHour = 0,
                deckPresetIdSnapshot = 1L,
            ),
        )

    fun log(
        session: StudySession,
        cardId: Long,
        rating: Rating,
        userId: Long = session.userId,
    ): ReviewLog =
        reviewLogRepository.save(
            ReviewLog(
                studySession = session,
                cardId = cardId,
                userId = userId,
                rating = rating,
                timeMs = 1000,
                reviewType = ReviewType.NEW,
                previousIntervalDays = 0,
                previousEaseFactor = BigDecimal("2.50"),
                previousCardStatus = CardLearningStatus.NEW,
                intervalDays = 1,
                easeFactor = BigDecimal("2.50"),
            ),
        )

    describe("getCounts") {
        it("평가 횟수, 고유 카드 수, 학습일 수를 구분하고 평가별 합계가 평가 횟수와 같다") {
            val s1 = session(day1)
            val s2 = session(day2)
            log(s1, cardId = 10L, rating = Rating.EASY)
            log(s1, cardId = 11L, rating = Rating.AGAIN)
            log(s2, cardId = 10L, rating = Rating.FAIR)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val counts = studyReportService.getCounts(USER_ID, deckId = null, period = null, maxReviewId = max)

            counts.reviewCount shouldBe 3
            counts.uniqueCardCount shouldBe 2
            counts.studyDayCount shouldBe 2
            counts.ratings shouldBe StudyReportRatingCountsDto(easy = 1, fair = 1, again = 1)
        }

        it("두 덱을 같은 날 학습해도 전체 학습일은 하루다") {
            log(session(day1, deckId = DECK_ID), cardId = 10L, rating = Rating.EASY)
            log(session(day1, deckId = OTHER_DECK_ID), cardId = 20L, rating = Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getCounts(USER_ID, null, null, max).studyDayCount shouldBe 1
            studyReportService.getCounts(USER_ID, DECK_ID, null, max).reviewCount shouldBe 1
        }

        it("벼락치기 세션과 다른 사용자의 로그는 집계하지 않는다") {
            log(session(day1), cardId = 10L, rating = Rating.EASY)
            log(session(day2, studyType = StudyType.FULL_DECK), cardId = 10L, rating = Rating.EASY)
            log(session(day1, userId = OTHER_USER_ID), cardId = 30L, rating = Rating.EASY)
            val max = maxOf(studyReportService.getMaxReviewId(USER_ID), studyReportService.getMaxReviewId(OTHER_USER_ID))

            studyReportService.getCounts(USER_ID, null, null, max).reviewCount shouldBe 1
        }

        it("완료하지 않은 세션의 저장된 평가도 포함한다") {
            log(session(day1, status = StudySessionStatus.STOPPED), cardId = 10L, rating = Rating.EASY)
            log(session(day2, status = StudySessionStatus.ACTIVE), cardId = 11L, rating = Rating.FAIR)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getCounts(USER_ID, null, null, max).reviewCount shouldBe 2
        }

        it("기간은 시작일과 종료일을 포함한다") {
            log(session(day1), cardId = 10L, rating = Rating.EASY)
            log(session(day2), cardId = 10L, rating = Rating.EASY)
            log(session(day3), cardId = 10L, rating = Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getCounts(USER_ID, null, StudyReportPeriod(day2, day3), max).reviewCount shouldBe 2
            studyReportService.getCounts(USER_ID, null, StudyReportPeriod(day2, day2), max).reviewCount shouldBe 1
        }

        it("기준 시점 이후에 저장된 평가는 읽지 않는다") {
            val s1 = session(day1)
            log(s1, cardId = 10L, rating = Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)
            log(s1, cardId = 11L, rating = Rating.EASY)

            studyReportService.getCounts(USER_ID, null, null, max).reviewCount shouldBe 1
        }

        it("기록이 없으면 모두 0이다") {
            val counts = studyReportService.getCounts(USER_ID, null, null, studyReportService.getMaxReviewId(USER_ID))

            counts.reviewCount shouldBe 0
            counts.ratings shouldBe StudyReportRatingCountsDto(0, 0, 0)
        }
    }

    describe("getDailyCounts") {
        it("평가가 있는 학습일만 날짜순으로 반환한다") {
            log(session(day1), cardId = 10L, rating = Rating.EASY)
            log(session(day3), cardId = 10L, rating = Rating.AGAIN)
            log(session(day3, deckId = OTHER_DECK_ID), cardId = 20L, rating = Rating.FAIR)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val daily = studyReportService.getDailyCounts(USER_ID, null, StudyReportPeriod(day1, day3), max)

            daily.map { it.studyDate to it.reviewCount } shouldContainExactly listOf(day1 to 1L, day3 to 2L)
            daily.last().ratings shouldBe StudyReportRatingCountsDto(easy = 0, fair = 1, again = 1)
        }
    }

    describe("getHistoryDeckIds / getFirstStudyDate") {
        it("일일 학습 평가가 있는 덱과 첫 학습일을 반환한다") {
            log(session(day2, deckId = DECK_ID), cardId = 10L, rating = Rating.EASY)
            log(session(day3, deckId = OTHER_DECK_ID), cardId = 20L, rating = Rating.EASY)
            session(day1, deckId = 99L) // 평가 없는 세션
            log(session(day1, deckId = 98L, studyType = StudyType.FULL_DECK), cardId = 30L, rating = Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getHistoryDeckIds(USER_ID) shouldBe setOf(DECK_ID, OTHER_DECK_ID)
            studyReportService.getFirstStudyDate(USER_ID, null, max) shouldBe day2
            studyReportService.getFirstStudyDate(USER_ID, OTHER_DECK_ID, max) shouldBe day3
            studyReportService.getFirstStudyDate(OTHER_USER_ID, null, max).shouldBeNull()
        }
    }

    describe("getRestDates") {
        it("기간 안에 휴식일로 기록된 날짜만 반환한다") {
            studyDayRepository.save(StudyDay(USER_ID, StreakType.REST_DAY, day1))
            studyDayRepository.save(StudyDay(USER_ID, StreakType.DAILY_STUDY, day2))
            studyDayRepository.save(StudyDay(USER_ID, StreakType.REST_DAY, day3.plusDays(1)))
            studyDayRepository.save(StudyDay(OTHER_USER_ID, StreakType.REST_DAY, day2))

            studyReportService.getRestDates(USER_ID, StudyReportPeriod(day1, day3)) shouldBe setOf(day1)
        }
    }

    describe("getSessions") {
        it("평가가 있는 세션을 (학습일, 세션 id) 내림차순으로 나눠 읽는다") {
            val s1 = session(day1)
            val s2a = session(day2, deckId = DECK_ID)
            val s2b = session(day2, deckId = OTHER_DECK_ID)
            session(day3) // 평가 없는 세션은 제외
            log(s1, 10L, Rating.EASY)
            log(s2a, 10L, Rating.FAIR)
            log(s2a, 11L, Rating.AGAIN)
            log(s2b, 20L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val first = studyReportService.getSessions(USER_ID, null, null, after = null, maxReviewId = max, limit = 2)
            first.map { it.sessionId } shouldContainExactly listOf(s2b.id, s2a.id).sortedDescending()
            val last = first.last()
            val second = studyReportService.getSessions(
                USER_ID,
                null,
                null,
                after = StudyReportSessionKey(last.studyDate, last.sessionId),
                maxReviewId = max,
                limit = 2,
            )
            second.map { it.sessionId } shouldContainExactly listOf(s1.id)

            val a = (first + second).first { it.sessionId == s2a.id }
            a.counts.reviewCount shouldBe 2
            a.counts.uniqueCardCount shouldBe 2
            a.counts.studyDayCount shouldBe 1
            a.counts.ratings shouldBe StudyReportRatingCountsDto(easy = 0, fair = 1, again = 1)
        }

        it("덱과 기간 조건을 적용한다") {
            log(session(day1, deckId = DECK_ID), 10L, Rating.EASY)
            log(session(day2, deckId = DECK_ID), 10L, Rating.EASY)
            log(session(day2, deckId = OTHER_DECK_ID), 20L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val result = studyReportService.getSessions(USER_ID, DECK_ID, StudyReportPeriod(day2, day2), null, max, 20)

            result.map { it.deckId to it.studyDate } shouldContainExactly listOf(DECK_ID to day2)
        }
    }

    describe("getCards") {
        it("기간 안의 마지막 평가와 평가 횟수를 마지막 평가가 최신인 순서로 반환한다") {
            val s1 = session(day1)
            val s2 = session(day2)
            log(s1, 10L, Rating.EASY)
            log(s1, 11L, Rating.AGAIN)
            log(s2, 10L, Rating.FAIR)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val cards = studyReportService.getCards(USER_ID, DECK_ID, null, null, null, max, 20)

            cards.map { Triple(it.cardId, it.lastRating, it.reviewCount) } shouldContainExactly listOf(
                Triple(10L, Rating.FAIR, 2L),
                Triple(11L, Rating.AGAIN, 1L),
            )
            cards.first().lastStudyDate shouldBe day2
        }

        it("평가 필터는 기간 안의 마지막 평가에 적용한다") {
            val s1 = session(day1)
            val s2 = session(day2)
            log(s1, 10L, Rating.EASY)
            log(s2, 10L, Rating.FAIR)
            log(s2, 11L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getCards(USER_ID, DECK_ID, null, Rating.EASY, null, max, 20)
                .map { it.cardId } shouldContainExactly listOf(11L)
            studyReportService.getCards(USER_ID, DECK_ID, StudyReportPeriod(day1, day1), Rating.EASY, null, max, 20)
                .map { it.cardId to it.reviewCount } shouldContainExactly listOf(10L to 1L)
        }

        it("마지막 평가 id 이전 카드부터 이어서 읽는다") {
            val s1 = session(day1)
            log(s1, 10L, Rating.EASY)
            log(s1, 11L, Rating.EASY)
            log(s1, 12L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val first = studyReportService.getCards(USER_ID, DECK_ID, null, null, null, max, 2)
            val second = studyReportService.getCards(USER_ID, DECK_ID, null, null, first.last().lastReviewId, max, 2)

            first.map { it.cardId } shouldContainExactly listOf(12L, 11L)
            second.map { it.cardId } shouldContainExactly listOf(10L)
        }

        it("다른 덱이나 다른 사용자의 로그는 읽지 않는다") {
            log(session(day1, deckId = OTHER_DECK_ID), 10L, Rating.EASY)
            log(session(day1, userId = OTHER_USER_ID), 10L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(OTHER_USER_ID)

            studyReportService.getCards(USER_ID, DECK_ID, null, null, null, max, 20).shouldBeEmpty()
        }
    }

    describe("getCardReviews") {
        it("카드의 평가 이력을 최신순으로 나눠 읽는다") {
            val s1 = session(day1)
            val s2 = session(day2)
            val l1 = log(s1, 10L, Rating.EASY)
            log(s1, 11L, Rating.EASY)
            val l2 = log(s2, 10L, Rating.AGAIN)
            val max = studyReportService.getMaxReviewId(USER_ID)

            val first = studyReportService.getCardReviews(USER_ID, DECK_ID, 10L, null, null, max, 1)
            val second = studyReportService.getCardReviews(USER_ID, DECK_ID, 10L, null, first.last().reviewId, max, 1)

            first.map { it.reviewId } shouldContainExactly listOf(l2.id)
            second.map { it.reviewId } shouldContainExactly listOf(l1.id)
            first.single().studyDate shouldBe day2
            first.single().timezone shouldBe kst
            first.single().reviewedAt shouldBe l2.createdAt
        }

        it("기간 밖의 이력은 읽지 않는다") {
            log(session(day1), 10L, Rating.EASY)
            log(session(day2), 10L, Rating.EASY)
            val max = studyReportService.getMaxReviewId(USER_ID)

            studyReportService.getCardReviews(USER_ID, DECK_ID, 10L, StudyReportPeriod(day2, day3), null, max, 20)
                .map { it.studyDate } shouldContainExactly listOf(day2)
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
        private const val DECK_ID = 1L
        private const val OTHER_DECK_ID = 2L
    }
}
