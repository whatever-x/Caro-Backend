package com.whatever.caro.study.internal

import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@Component
class StaleSessionSweeper(
    private val studySessionRepository: StudySessionRepository,
    private val clock: Clock,
) {

    @Scheduled(cron = $$"${app.cron.stale-session-sweep:-}")
    fun stopStaleSessions() {
        val before = LocalDate.now(clock).minusDays(GRACE_DAYS)
        val stoppedCount = studySessionRepository.stopStaledActiveBefore(before = before)
        logger.info { "Stale active study sessions stopped. before=$before count=$stoppedCount" }
    }

    companion object {
        /**
         * 클라이언트의 오늘(sessionDate)은 서버 UTC date보다 최대 2일까지 뒤처질 수 있기 때문에
         * 진행중인 세션을 정지하지 않도록 3일의 유예를 둔다.
         */
        private const val GRACE_DAYS = 3L
    }
}
