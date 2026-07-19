package com.whatever.caro.study.internal

import com.whatever.caro.study.StudySessionStatus

data class EvaluationResult(
    val evaluatedItems: List<ValidItem>,
    val failedItems: List<InvalidItem>,
    val sessionStatus: StudySessionStatus,
    val ratingCounts: RatingCounts,
)

data class RatingCounts(
    val again: Int,
    val fair: Int,
    val easy: Int,
)
