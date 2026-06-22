package com.whatever.caro.study

import java.time.LocalDate

data class DailyStudyCompletedEvent(
    val userId: Long,
    val studyDate: LocalDate,
)
