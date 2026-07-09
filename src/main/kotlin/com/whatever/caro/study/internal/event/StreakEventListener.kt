package com.whatever.caro.study.internal.event

import com.whatever.caro.study.DailyStudyCompletedEvent
import com.whatever.caro.study.internal.streak.StreakService
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.stereotype.Component

@Component
class StreakEventListener(
    private val streakService: StreakService,
) {
    @ApplicationModuleListener
    fun onDailyStudyCompleted(
        event: DailyStudyCompletedEvent,
    ) {
        streakService.recordStudied(
            userId = event.userId,
            studyDate = event.studyDate,
        )
    }
}
