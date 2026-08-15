package com.whatever.caro.common.resubmit

import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.TimeUnit

@Component
class EventResubmitScheduler(
    private val incompleteEventPublications: IncompleteEventPublications,
) {

    /**
     * 1. 10분 마다 수행,
     * 2. 20분이 지나도록 성공하지 못한 건 대해서
     * 3. 3회까지 재시도해봄
     */
    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.MINUTES) // 10분마다 수행
    fun resubmit() {
        incompleteEventPublications.resubmitIncompletePublications(
            ResubmissionOptions.defaults()
                .withMinAge(Duration.ofMinutes(20))
                .withFilter { it.completionAttempts < 3 },
        )
    }
}
