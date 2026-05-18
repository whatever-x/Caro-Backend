package com.whatever.caro.study

import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StudyTargetPoolCalculatorTest :
    DescribeSpec({
        val kstZoneId = ZoneId.of("Asia/Seoul")
        val baseNow = Instant.parse("2026-05-18T10:00:00Z")

        fun createCalculator(): Pair<StudyTargetPoolCalculator, CardLearningStateRepository> {
            val repo = mockk<CardLearningStateRepository>(relaxed = true)
            val calc = StudyTargetPoolCalculator(cardLearningStateRepository = repo)
            return calc to repo
        }

        describe("getTodayPool") {
            it("학습 대상인 NEW/REVIEW 카드의 개수가 일일학습 최대 한도보다 많아도, 한도까지만 반환된다") {
                val (calc, repo) = createCalculator()
                every { repo.countNewCards(any(), any()) } returns 30
                every { repo.countTodayReviewCards(any(), any(), any()) } returns 30

                val newCardLimit = 20
                val reviewCardLimit = 20
                val result = calc.getTodayPool(
                    now = baseNow,
                    userId = 1L,
                    timezone = kstZoneId,
                    deckId = 1L,
                    newCardPerDay = newCardLimit,
                    reviewCardPerDay = reviewCardLimit,
                    dayCutoffHour = 0,
                )

                result.newCount shouldBe newCardLimit
                result.reviewCount shouldBe reviewCardLimit
            }

            it("NEW/REVIEW 카드 개수가 일일 학습 한도보다 적은 경우, NEW/REVIEW 카드의 개수를 반환한다") {
                val (calc, repo) = createCalculator()
                val existingNewCardCount = 5
                val existingReviewCardCount = 3
                every { repo.countNewCards(any(), any()) } returns existingNewCardCount
                every { repo.countTodayReviewCards(any(), any(), any()) } returns existingReviewCardCount

                val result = calc.getTodayPool(
                    now = baseNow,
                    userId = 1L,
                    timezone = kstZoneId,
                    deckId = 1L,
                    newCardPerDay = 20,
                    reviewCardPerDay = 20,
                    dayCutoffHour = 0,
                )

                result.newCount shouldBe existingNewCardCount
                result.reviewCount shouldBe existingReviewCardCount
            }
        }

        describe("getTodayPool - 04시 기준 cutoff nextSessionStart 시간 계산") {
            data class TimezoneCase(
                val requestedAt: String,
                val timezone: ZoneId,
                val expectedNextSessionStart: String,
                val label: String,
            )

            val cases = sequenceOf(
                TimezoneCase(
                    requestedAt = "2026-05-18T19:00:00",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 18일 19:00 요청일 경우, KST 19일 04:00이 다음 세션 시작 시간이다",
                ),
                TimezoneCase(
                    requestedAt = "2026-05-19T03:59:59",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 19일 03:59:59 요청(컷오프 1초 직전)일 경우, KST 19일 04:00이 다음 세션 시작 시간이다",
                ),
                TimezoneCase(
                    requestedAt = "2026-05-19T04:00:00",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-20T04:00:00",
                    label = "KST 19일 04:00 요청(컷오프 정각)일 경우, KST 20일 04:00이 다음 세션 시작 시간이다",
                ),
                TimezoneCase(
                    requestedAt = "2026-05-19T04:00:01",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-20T04:00:00",
                    label = "KST 19일 04:00:01 요청(컷오프 1초 직후)일 경우, KST 20일 04:00이 다음 세션 시작 시간이다",
                ),
                TimezoneCase(
                    requestedAt = "2026-05-18T23:59:59",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 18일 23:59:59 요청일 경우, KST 19일 04:00이 다음 세션 시작 시간이다(자정이 아닌 cutoff 기준 세션 분기)",
                ),
                TimezoneCase(
                    requestedAt = "2026-05-19T00:00:01",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 19일 00:00:01 요청일 경우, KST 19일 04:00이 다음 세션 시작 시간이다(자정이 아닌 cutoff 기준 세션 분기)",
                ),
            )

            withData(
                nameFn = { it.label },
                cases,
            ) { case ->
                val (calc, repo) = createCalculator()
                every { repo.countTodayReviewCards(any(), any(), any()) } returns 0

                calc.getTodayPool(
                    now = LocalDateTime.parse(case.requestedAt).atZone(case.timezone).toInstant(),
                    userId = 1L,
                    timezone = case.timezone,
                    deckId = 1L,
                    newCardPerDay = 0,
                    reviewCardPerDay = 0,
                    dayCutoffHour = 4, // 해당 timezone의 04:00 cutoff
                )

                verify(exactly = 1) {
                    repo.countTodayReviewCards(
                        userId = 1L,
                        deckId = 1L,
                        nextSessionStart = LocalDateTime.parse(case.expectedNextSessionStart).atZone(case.timezone).toInstant(),
                    )
                }
            }
        }
    })
