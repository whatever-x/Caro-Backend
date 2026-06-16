package com.whatever.caro.study.internal

import com.whatever.caro.study.StudySessionStatus

data class EvaluationResult(
    val evaluatedItems: List<ValidItem>,
    val failedItems: List<InvalidItem>,
    val sessionStatus: StudySessionStatus,
)
