package com.whatever.caro.study.internal

import com.whatever.caro.study.Rating
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

sealed interface SchedulingState {
    val easeFactor: BigDecimal
    val intervalDays: Int

    /**
     * 연속 Again 평가 횟수. client 뱃지 표시용 카운터이며, SRS와 무관
     */
    val consecutiveAgainCount: Int

    fun nextStates(
        context: SchedulingContext,
    ): SchedulingStates

    data class New(
        override val easeFactor: BigDecimal,
        override val consecutiveAgainCount: Int,
    ) : SchedulingState {
        override val intervalDays: Int = 0
        override fun nextStates(
            context: SchedulingContext,
        ): SchedulingStates =
            SchedulingStates(
                again = answerAgain(),
                fair = answerFair(context),
                easy = answerEasy(context),
            )

        private fun answerEasy(
            context: SchedulingContext,
        ): Review {
            val newEf = (easeFactor + EF_DELTA_EASY).coerceIn(MIN_EF, MAX_EF).setScale(2, RoundingMode.HALF_UP)
            return Review(
                easeFactor = newEf,
                intervalDays = context.params.newEasyInterval,
                repetitions = 1,
                lapses = 0,
                lastReviewedDate = context.studyDate,
                nextReviewDate = context.studyDate.plusDays(context.params.newEasyInterval.toLong()),
                consecutiveAgainCount = 0,
            )
        }

        private fun answerFair(
            context: SchedulingContext,
        ): Review {
            val newEf = easeFactor.setScale(2, RoundingMode.HALF_UP)
            return Review(
                easeFactor = newEf,
                intervalDays = context.params.newFairInterval,
                repetitions = 1,
                lapses = 0,
                lastReviewedDate = context.studyDate,
                nextReviewDate = context.studyDate.plusDays(context.params.newFairInterval.toLong()),
                consecutiveAgainCount = 0,
            )
        }

        private fun answerAgain(): New {
            val newEf = (easeFactor + EF_DELTA_AGAIN).coerceIn(MIN_EF, MAX_EF).setScale(2, RoundingMode.HALF_UP)
            return New(
                easeFactor = newEf,
                consecutiveAgainCount = consecutiveAgainCount + 1,
            )
        }
    }

    data class Review(
        override val easeFactor: BigDecimal,
        override val intervalDays: Int,
        val repetitions: Int,
        val lapses: Int,
        val lastReviewedDate: LocalDate?,
        val nextReviewDate: LocalDate?,
        override val consecutiveAgainCount: Int,
    ) : SchedulingState {
        override fun nextStates(
            context: SchedulingContext,
        ): SchedulingStates =
            SchedulingStates(
                again = answerAgain(context),
                fair = answerFair(context),
                easy = answerEasy(context),
            )

        private fun answerEasy(
            context: SchedulingContext,
        ): Review {
            val newEf = (easeFactor + EF_DELTA_EASY).coerceIn(MIN_EF, MAX_EF).setScale(2, RoundingMode.HALF_UP)
            val newIntervalDays = (intervalDays.toBigDecimal() * newEf)
                .coerceAtLeast((intervalDays + 1).toBigDecimal())
                .coerceAtMost(context.params.reviewMaxInterval.toBigDecimal())
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
            return Review(
                easeFactor = newEf,
                intervalDays = newIntervalDays,
                repetitions = repetitions + 1,
                lapses = lapses,
                lastReviewedDate = context.studyDate,
                nextReviewDate = context.studyDate.plusDays(newIntervalDays.toLong()),
                consecutiveAgainCount = 0,
            )
        }

        private fun answerFair(
            context: SchedulingContext,
        ): Review {
            val newIntervalDays = (intervalDays.toBigDecimal() * easeFactor)
                .coerceAtLeast((intervalDays + 1).toBigDecimal())
                .coerceAtMost(context.params.reviewMaxInterval.toBigDecimal())
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
            return Review(
                easeFactor = easeFactor,
                intervalDays = newIntervalDays,
                repetitions = repetitions + 1,
                lapses = lapses,
                lastReviewedDate = context.studyDate,
                nextReviewDate = context.studyDate.plusDays(newIntervalDays.toLong()),
                consecutiveAgainCount = 0,
            )
        }

        private fun answerAgain(
            context: SchedulingContext,
        ): Review {
            val newEf = (easeFactor + EF_DELTA_AGAIN).coerceIn(MIN_EF, MAX_EF).setScale(2, RoundingMode.HALF_UP)
            val newIntervalDays = (intervalDays.toBigDecimal() * context.params.lapseIntervalMultiplier)
                .coerceAtLeast(context.params.lapseMinInterval.toBigDecimal())
                .coerceAtMost(context.params.reviewMaxInterval.toBigDecimal())
                .setScale(0, RoundingMode.HALF_UP)
                .toInt()
            return Review(
                easeFactor = newEf,
                intervalDays = newIntervalDays,
                repetitions = 0,
                lapses = lapses + 1,
                lastReviewedDate = context.studyDate,
                nextReviewDate = context.studyDate.plusDays(newIntervalDays.toLong()),
                consecutiveAgainCount = consecutiveAgainCount + 1,
            )
        }
    }

    companion object {
        private val EF_DELTA_AGAIN = BigDecimal("-0.20")
        private val EF_DELTA_EASY = BigDecimal("0.15")
        private val MIN_EF = BigDecimal("1.30")
        private val MAX_EF = BigDecimal("5.00")
    }
}

data class SchedulingStates(
    val again: SchedulingState,
    val fair: SchedulingState,
    val easy: SchedulingState,
) {
    fun pick(
        rating: Rating,
    ): SchedulingState =
        when (rating) {
            Rating.AGAIN -> again
            Rating.FAIR -> fair
            Rating.EASY -> easy
        }
}

data class SchedulingContext(
    val studyDate: LocalDate,
    val params: Sm2Params,
)
