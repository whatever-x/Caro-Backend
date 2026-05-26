package com.whatever.caro.study

import java.time.Instant

interface StudyApi {
    /**
     * 오늘 학습 세션에 대한 정보를 반환한다.
     *
     * 시각은 caller(요청 진입 시점)가 결정한 단일 `now`를 전달받아 도메인 호출 간 일관성을 보장한다.
     */
    fun getTodaySummary(
        now: Instant,
        userId: Long,
        deckId: Long,
    ): TodaySummaryDto

    /**
     * 카드별 학습 상태를 cardId 기준 맵으로 반환한다.
     * 평가 이력이 없는 카드는 맵에 포함되지 않으므로, 호출 측에서 부재를 처리한다.
     */
    fun getLearningStates(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardLearningStateDto>
}
