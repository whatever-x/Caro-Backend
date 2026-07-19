package com.whatever.caro.study.internal.web.response

import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.internal.EvaluationResult
import com.whatever.caro.study.internal.RatingCounts
import io.swagger.v3.oas.annotations.media.Schema

data class EvaluationResponse(
    @Schema(description = "평가가 완료된 카드 id")
    val evaluatedCardIds: List<Long>,
    @Schema(description = "평가에 실패한 카드 id, 일반적인 케이스에서 나오지 않음")
    val failedCardIds: List<Long>,
    @Schema(description = "평가 시점 세션의 상태")
    val sessionStatus: StudySessionStatus,
    @Schema(description = "세션 전체 누적 등급별 카운트(중단 이전 평가 포함). again=모르겠어요, fair=애매해요, easy=쉬워요")
    val ratingCounts: RatingCounts,
) {
    companion object {
        fun from(
            result: EvaluationResult,
        ) = EvaluationResponse(
            evaluatedCardIds = result.evaluatedItems.map { it.item.cardId },
            failedCardIds = result.failedItems.map { it.item.cardId },
            sessionStatus = result.sessionStatus,
            ratingCounts = result.ratingCounts,
        )
    }
}
