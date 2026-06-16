package com.whatever.caro.study.internal.web.response

import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.internal.EvaluationResult

data class EvaluationResponse(
    val evaluatedCardIds: List<Long>,
    val failedCardIds: List<Long>,
    val sessionStatus: StudySessionStatus,
) {
    companion object {
        fun from(
            result: EvaluationResult,
        ) = EvaluationResponse(
            evaluatedCardIds = result.evaluatedItems.map { it.item.cardId },
            failedCardIds = result.failedItems.map { it.item.cardId },
            sessionStatus = result.sessionStatus,
        )
    }
}
