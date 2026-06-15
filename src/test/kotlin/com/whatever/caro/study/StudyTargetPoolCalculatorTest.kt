package com.whatever.caro.study

import com.whatever.caro.study.internal.DeckCardCount
import com.whatever.caro.study.internal.Sm2ParamsFixture
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.maps.shouldBeEmpty
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

        describe("getTodayPools") {
            it("덱별 카드 수가 perDay보다 많으면 perDay까지만, 적으면 카드 수 그대로 반환된다") {
                val (calc, repo) = createCalculator()
                val presetByDeckId = mapOf(
                    1L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 2, reviewPerDay = 1),
                    2L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10),
                )
                every { repo.countNewCardsByDeckIds(any()) } returns
                    listOf(DeckCardCount(1L, 5), DeckCardCount(2L, 4))
                every { repo.countReviewCardsByDeckIds(any(), any()) } returns
                    listOf(DeckCardCount(1L, 3), DeckCardCount(2L, 2))

                val result = calc.getTodayPools(
                    now = baseNow,
                    timezone = kstZoneId,
                    presetByDeckId = presetByDeckId,
                    dayCutoffHour = 0,
                )

                result[1L] shouldBe StudyTargetPoolCount(newCount = 2, reviewCount = 1)
                result[2L] shouldBe StudyTargetPoolCount(newCount = 4, reviewCount = 2)
            }

            it("count 결과에 행이 없는 덱은 pool 0으로 폴백된다") {
                val (calc, repo) = createCalculator()
                val presetByDeckId = mapOf(
                    1L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10),
                )
                every { repo.countNewCardsByDeckIds(any()) } returns emptyList()
                every { repo.countReviewCardsByDeckIds(any(), any()) } returns emptyList()

                val result = calc.getTodayPools(
                    now = baseNow,
                    timezone = kstZoneId,
                    presetByDeckId = presetByDeckId,
                    dayCutoffHour = 0,
                )

                result[1L] shouldBe StudyTargetPoolCount(newCount = 0, reviewCount = 0)
            }

            it("presetByDeckId가 비어있으면 count 쿼리 호출 없이 빈 맵을 반환한다") {
                val (calc, repo) = createCalculator()

                val result = calc.getTodayPools(
                    now = baseNow,
                    timezone = kstZoneId,
                    presetByDeckId = emptyMap(),
                    dayCutoffHour = 0,
                )

                result.shouldBeEmpty()
                verify(exactly = 0) { repo.countNewCardsByDeckIds(any()) }
                verify(exactly = 0) { repo.countReviewCardsByDeckIds(any(), any()) }
            }

            it("반환된 키 집합은 presetByDeckId의 키 집합과 같다") {
                val (calc, repo) = createCalculator()
                val presetByDeckId = mapOf(
                    1L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE,
                    2L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE,
                    3L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE,
                )
                every { repo.countNewCardsByDeckIds(any()) } returns emptyList()
                every { repo.countReviewCardsByDeckIds(any(), any()) } returns emptyList()

                val result = calc.getTodayPools(
                    now = baseNow,
                    timezone = kstZoneId,
                    presetByDeckId = presetByDeckId,
                    dayCutoffHour = 0,
                )

                result.keys shouldBe presetByDeckId.keys
            }
        }

        describe("getTodayPools - 04시 기준 cutoff nextSessionStart 시간 계산 (단건과 동일 규칙)") {
            data class BatchTimezoneCase(
                val requestedAt: String,
                val timezone: ZoneId,
                val expectedNextSessionStart: String,
                val label: String,
            )

            val cases = sequenceOf(
                BatchTimezoneCase(
                    requestedAt = "2026-05-18T19:00:00",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 18일 19:00 요청일 경우, KST 19일 04:00이 다음 세션 시작 시간이다",
                ),
                BatchTimezoneCase(
                    requestedAt = "2026-05-19T03:59:59",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-19T04:00:00",
                    label = "KST 19일 03:59:59 요청(컷오프 1초 직전)일 경우, KST 19일 04:00이 다음 세션 시작 시간이다",
                ),
                BatchTimezoneCase(
                    requestedAt = "2026-05-19T04:00:00",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-20T04:00:00",
                    label = "KST 19일 04:00 요청(컷오프 정각)일 경우, KST 20일 04:00이 다음 세션 시작 시간이다",
                ),
                BatchTimezoneCase(
                    requestedAt = "2026-05-19T04:00:01",
                    timezone = kstZoneId,
                    expectedNextSessionStart = "2026-05-20T04:00:00",
                    label = "KST 19일 04:00:01 요청(컷오프 1초 직후)일 경우, KST 20일 04:00이 다음 세션 시작 시간이다",
                ),
            )

            withData(
                nameFn = { it.label },
                cases,
            ) { case ->
                val (calc, repo) = createCalculator()
                every { repo.countNewCardsByDeckIds(any()) } returns emptyList()
                every { repo.countReviewCardsByDeckIds(any(), any()) } returns emptyList()

                calc.getTodayPools(
                    now = LocalDateTime.parse(case.requestedAt).atZone(case.timezone).toInstant(),
                    timezone = case.timezone,
                    presetByDeckId = mapOf(1L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE),
                    dayCutoffHour = 4, // 해당 timezone의 04:00 cutoff
                )

                verify(exactly = 1) {
                    repo.countReviewCardsByDeckIds(
                        deckIds = setOf(1L),
                        nextSessionStart = LocalDateTime.parse(case.expectedNextSessionStart).atZone(case.timezone).toInstant(),
                    )
                }
            }
        }
    })
