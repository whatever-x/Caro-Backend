package com.whatever.caro.study.internal

import com.whatever.caro.study.Rating
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant

class EvaluationItemValidatorTest :
    DescribeSpec({

        val now: Instant = Instant.parse("2026-05-19T01:00:00Z")

        fun item(
            cardId: Long = 1L,
            timeMs: Int = 1000,
            rating: Rating = Rating.FAIR,
        ): EvaluatedCardDto =
            EvaluatedCardDto(
                cardId = cardId,
                rating = rating,
                timeMs = timeMs,
            )

        describe("validate") {

            context("평가가 가능한 Item일 때") {
                it("timeMs=0이면 ValidatedItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(timeMs = 0),
                        evaluatedCardIds = emptySet(),
                    )

                    result.shouldBeInstanceOf<ValidItem>()
                }

                it("timeMs=600000이면 ValidatedItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(timeMs = 600_000),
                        evaluatedCardIds = emptySet(),
                    )

                    result.shouldBeInstanceOf<ValidItem>()
                }

                it("평가되지 않은 cardId는 ValidatedItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(cardId = 1L),
                        evaluatedCardIds = emptySet(),
                    )

                    result.shouldBeInstanceOf<ValidItem>()
                }
            }

            context("평가가 불가능한 Item일 때") {
                it("timeMs=-1이면 InvalidItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(timeMs = -1),
                        evaluatedCardIds = emptySet(),
                    )

                    result.shouldBeInstanceOf<InvalidItem>()
                }

                it("timeMs=600001이면 InvalidItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(timeMs = 600_001),
                        evaluatedCardIds = emptySet(),
                    )

                    result.shouldBeInstanceOf<InvalidItem>()
                }

                it("이미 평가된 cardId는 InvalidItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(cardId = 1L),
                        evaluatedCardIds = setOf(1L),
                    )

                    result.shouldBeInstanceOf<InvalidItem>()
                }

                it("timeMs 무효와 이미 평가됨이 동시일 때 InvalidItem을 반환한다") {
                    val result = EvaluationItemValidator.validate(
                        item = item(cardId = 1L, timeMs = -1),
                        evaluatedCardIds = setOf(1L),
                    )

                    result.shouldBeInstanceOf<InvalidItem>()
                }
            }
        }
    })
