package com.whatever.caro.study.internal

import java.math.BigDecimal

data class Sm2Params(
    val newFairInterval: Int,
    val newEasyInterval: Int,
    val newInitialEaseFactor: BigDecimal,
    val reviewMaxInterval: Int,
    val lapseIntervalMultiplier: BigDecimal,
    val lapseMinInterval: Int,
    val leechThreshold: Int,
)
