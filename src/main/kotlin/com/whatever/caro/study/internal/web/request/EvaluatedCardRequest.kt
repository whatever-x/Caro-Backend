package com.whatever.caro.study.internal.web.request

import com.whatever.caro.study.Rating
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive

data class EvaluatedCardRequest(
    @field:Positive val cardId: Long,
    val rating: Rating,
    @field:Min(0) val timeMs: Int,
)
