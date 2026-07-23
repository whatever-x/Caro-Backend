package com.whatever.caro.study.internal.streak

sealed interface StreakStatusResult {
    data object NotStarted : StreakStatusResult
    data class Active(
        val currentStreak: Int,
    ) : StreakStatusResult
    data object Broken : StreakStatusResult
}
