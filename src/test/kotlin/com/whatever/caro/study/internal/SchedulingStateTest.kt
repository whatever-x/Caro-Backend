package com.whatever.caro.study.internal

import com.whatever.caro.study.Rating
import com.whatever.caro.study.internal.SchedulingState.New
import com.whatever.caro.study.internal.SchedulingState.Review
import com.whatever.caro.study.internal.Sm2ParamsFixture.SM2_PARAMS_FIXTURE
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class SchedulingStateTest :
    DescribeSpec({

        val baseDate: LocalDate = LocalDate.parse("2026-06-03")
        val context = SchedulingContext(studyDate = baseDate, params = SM2_PARAMS_FIXTURE)

        val efDeltaAgain = BigDecimal("0.20")
        val efDeltaEasy = BigDecimal("0.15")
        describe("New.nextStates(...).pick(rating)") {

            it("AGAIN 평가 시 ef는 -0.20되고 New로 유지된다") {
                val state = New(easeFactor = BigDecimal("2.50"), consecutiveAgainCount = 0)

                val result = state.nextStates(context).pick(Rating.AGAIN)

                result.shouldBeInstanceOf<New>()
                result.easeFactor shouldBe state.easeFactor.minus(efDeltaAgain)
                result.consecutiveAgainCount shouldBe 1
                result.intervalDays shouldBe 0
            }

            it("FAIR 평가 시 ef는 유지되고 Review로 졸업한다") {
                val state = New(easeFactor = BigDecimal("2.50"), consecutiveAgainCount = 2)

                val result = state.nextStates(context).pick(Rating.FAIR)

                result.shouldBeInstanceOf<Review>()
                result.easeFactor shouldBe BigDecimal("2.50")
                result.intervalDays shouldBe SM2_PARAMS_FIXTURE.newFairInterval
                result.repetitions shouldBe 1
                result.lapses shouldBe 0
                result.consecutiveAgainCount shouldBe 0
                result.lastReviewedDate shouldBe baseDate
                result.nextReviewDate shouldBe baseDate.plus(SM2_PARAMS_FIXTURE.newFairInterval.toLong(), ChronoUnit.DAYS)
            }

            it("EASY 평가 시 ef는 +0.15되고 Review로 졸업한다") {
                val state = New(easeFactor = BigDecimal("2.50"), consecutiveAgainCount = 0)

                val result = state.nextStates(context).pick(Rating.EASY)

                result.shouldBeInstanceOf<Review>()
                result.easeFactor shouldBe state.easeFactor.plus(efDeltaEasy)
                result.intervalDays shouldBe SM2_PARAMS_FIXTURE.newEasyInterval
                result.repetitions shouldBe 1
                result.lapses shouldBe 0
                result.nextReviewDate shouldBe baseDate.plus(SM2_PARAMS_FIXTURE.newEasyInterval.toLong(), ChronoUnit.DAYS)
            }

            it("AGAIN 평가 시 ef는 1.30 미만으로 감소하지 않는다") {
                val minEf = BigDecimal("1.30")
                val state = New(easeFactor = minEf, consecutiveAgainCount = 6)

                val result = state.nextStates(context).pick(Rating.AGAIN)

                result.easeFactor shouldBe minEf
                result.consecutiveAgainCount shouldBe state.consecutiveAgainCount + 1
            }

            it("EASY 평가 시 ef는 5.00 초과로 증가하지 않는다") {
                val maxEf = BigDecimal("5.00")
                val state = New(easeFactor = maxEf, consecutiveAgainCount = 0)

                val result = state.nextStates(context).pick(Rating.EASY)

                result.easeFactor shouldBe maxEf
            }

            it("FAIR/EASY 평가 시 누적된 consecutiveAgainCount가 0으로 리셋된다") {
                val state = New(easeFactor = BigDecimal("2.50"), consecutiveAgainCount = 3)

                val result = state.nextStates(context).pick(Rating.FAIR)

                result.consecutiveAgainCount shouldBe 0
            }
        }

        describe("Review.nextStates(...).pick(rating)") {

            it("AGAIN 평가 시 ef -0.20, interval = max(lapseMin, interval*multiplier), lapses+1") {
                val state = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = 20,
                    repetitions = 5,
                    lapses = 1,
                    lastReviewedDate = baseDate.minusDays(20L),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 0,
                )

                val result = state.nextStates(context).pick(Rating.AGAIN)

                result.shouldBeInstanceOf<Review>()
                result.easeFactor shouldBe state.easeFactor.minus(efDeltaAgain)
                result.intervalDays shouldBe 10 // 20 * 0.5 = 10.0, coerceIn(lapseMin=1, reviewMax=180) -> 10
                result.repetitions shouldBe 0
                result.lapses shouldBe 2
                result.consecutiveAgainCount shouldBe 1
            }

            it("AGAIN 평가 시 newInterval이 lapseMinInterval보다 작아지지 않는다") {
                val state = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = 1,
                    repetitions = 1,
                    lapses = 0,
                    lastReviewedDate = baseDate.minusDays(1L),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 0,
                )

                val result = state.nextStates(context).pick(Rating.AGAIN)

                // 1 * 0.5 = 0.5, coerceIn(lapseMin=1, 180) -> 1
                result.intervalDays shouldBe SM2_PARAMS_FIXTURE.lapseMinInterval
            }

            it("FAIR 평가 시 interval = interval*ef, ef 유지, repetitions+1") {
                val state = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = 10,
                    repetitions = 2,
                    lapses = 3,
                    lastReviewedDate = baseDate.minusDays(10),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 1,
                )

                val result = state.nextStates(context).pick(Rating.FAIR)

                result.shouldBeInstanceOf<Review>()
                result.intervalDays shouldBe 25
                result.easeFactor shouldBe BigDecimal("2.50")
                result.repetitions shouldBe 3
                result.lapses shouldBe 3
                result.consecutiveAgainCount shouldBe 0
            }

            it("FAIR 평가 시 newInterval이 reviewMaxInterval보다 커지지 않는다") {
                val state = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = 100,
                    repetitions = 5,
                    lapses = 0,
                    lastReviewedDate = baseDate.minusDays(100L),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 0,
                )

                val result = state.nextStates(context).pick(Rating.FAIR)

                // 100 * 2.50 = 250 > reviewMaxInterval(180)
                result.intervalDays shouldBe SM2_PARAMS_FIXTURE.reviewMaxInterval
            }

            it("FAIR 평가 시 newInterval이 intervalDays+1 이상으로 보장된다") {
                // EF가 1.x 대이고, interval days가 1일 경우, 정답을 맞춰도 학습 간격일이 1일로 고정되어버림
                // 이를 방지하기 위해 최솟값을 interval days + 1로 설정하여 고정을 방지
                val state = Review(
                    easeFactor = BigDecimal("1.30"),
                    intervalDays = 1, // 1 * 1.30 = 1.30, coerceIn(2, 180) -> 2
                    repetitions = 1,
                    lapses = 0,
                    lastReviewedDate = baseDate.minusDays(1L),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 6,
                )

                val result = state.nextStates(context).pick(Rating.FAIR)

                result.intervalDays shouldBe state.intervalDays.plus(1)
            }

            it("EASY 평가 시 ef +0.15, interval = interval*newEf, repetitions+1") {
                val state = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = 10,
                    repetitions = 2,
                    lapses = 0,
                    lastReviewedDate = baseDate.minusDays(10L),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 2,
                )

                val result = state.nextStates(context).pick(Rating.EASY)

                result.shouldBeInstanceOf<Review>()
                result.easeFactor shouldBe state.easeFactor.plus(efDeltaEasy)
                result.intervalDays shouldBe 27 // 10 * 2.65 = 26.50, coerceIn(11, 180) -> 26.50, HALF_UP -> 27
                result.repetitions shouldBe 3
                result.consecutiveAgainCount shouldBe 0
            }

            it("interval은 EASY/FAIR로 평가해도 reviewMaxInterval보다 커지지 않는다.") {
                val maxInterval = 180
                val reviewState = Review(
                    easeFactor = BigDecimal("2.50"),
                    intervalDays = maxInterval,
                    repetitions = 5,
                    lapses = 0,
                    lastReviewedDate = baseDate.minusDays(maxInterval.toLong()),
                    nextReviewDate = baseDate,
                    consecutiveAgainCount = 0,
                )

                // 일반적인 경우 정답 처리 시 interval은 증가하지만, 이미 최대치일 경우 상한에 막힌다.
                reviewState.nextStates(context).pick(Rating.FAIR).intervalDays shouldBe maxInterval
                reviewState.nextStates(context).pick(Rating.EASY).intervalDays shouldBe maxInterval
            }
        }

        describe("SchedulingStates.pick(rating)") {

            it("rating에 따라 명시된 state가 반환된다") {
                val state = New(easeFactor = BigDecimal("2.50"), consecutiveAgainCount = 0)
                val states = state.nextStates(context)

                states.pick(Rating.AGAIN) shouldBe states.again
                states.pick(Rating.FAIR) shouldBe states.fair
                states.pick(Rating.EASY) shouldBe states.easy
            }
        }
    })
