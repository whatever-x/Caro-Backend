package com.whatever.caro.study.internal

import com.whatever.caro.study.Rating

data class EvaluatedCardDto(
    val cardId: Long,
    val rating: Rating,
    val timeMs: Int,
)
