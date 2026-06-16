package com.whatever.caro.study

import java.time.Instant
import java.time.LocalDate

data class StudySessionDto(
    val sessionId: Long,
    val deckId: Long,
    val status: StudySessionStatus,
    val studyType: StudyType,
    val sessionDate: LocalDate,
    val newCardsStudied: Int,
    val reviewCardsStudied: Int,
    val newCardsGoal: Int,
    val reviewCardsGoal: Int,
    val estimatedTotal: Int,
    val startedAt: Instant,
    val endedAt: Instant?,
)
