package com.whatever.caro.study.internal

import com.whatever.caro.study.StudySessionStatus

data class EvaluationResult(
    val evaluatedItems: List<ValidationResult>,
    val failedItems: List<ValidationResult>,
    val sessionStatus: StudySessionStatus,
)
