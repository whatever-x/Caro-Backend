package com.whatever.caro.study.internal

import com.whatever.caro.study.Rating
import java.time.Instant

data class EvaluatedCardDto(
    val cardId: Long,
    val rating: Rating,
    val timeMs: Int,
    val evaluatedAt: Instant,
)
