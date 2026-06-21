package com.whatever.caro.study

import java.time.LocalDate

data class DailyStudyCompletedEvnet(
    val userId: Long,
    val studyDate: LocalDate,
)
