package com.whatever.caro.study.internal

import com.whatever.caro.card.DeckPresetDto
import java.math.BigDecimal

/**
 * SchedulingStateTest 와 EvaluationServiceTest 가 공유하는 SM-2 파라미터 fixture.
 * 운영에서 사용되는 값과는 다르다.
 */
object Sm2ParamsFixture {

    val SM2_PARAMS_FIXTURE: Sm2Params = Sm2Params(
        newFairInterval = 1,
        newEasyInterval = 7,
        newInitialEaseFactor = BigDecimal("2.50"),
        reviewMaxInterval = 180,
        lapseIntervalMultiplier = BigDecimal("0.5"),
        lapseMinInterval = 1,
        leechThreshold = 8,
    )

    val DECK_PRESET_DTO_FIXTURE: DeckPresetDto = DeckPresetDto(
        id = 1L,
        userId = 1L,
        name = "test-preset",
        newPerDay = 20,
        newFairInterval = SM2_PARAMS_FIXTURE.newFairInterval,
        newEasyInterval = SM2_PARAMS_FIXTURE.newEasyInterval,
        newInitialEaseFactor = SM2_PARAMS_FIXTURE.newInitialEaseFactor,
        reviewPerDay = 40,
        reviewMaxInterval = SM2_PARAMS_FIXTURE.reviewMaxInterval,
        lapseIntervalMultiplier = SM2_PARAMS_FIXTURE.lapseIntervalMultiplier,
        lapseMinInterval = SM2_PARAMS_FIXTURE.lapseMinInterval,
        leechThreshold = SM2_PARAMS_FIXTURE.leechThreshold,
        hardBadgeThreshold = 3,
    )
}
