package com.whatever.caro.study.internal.studysession

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ReviewLogRepository : JpaRepository<ReviewLog, Long> {
    @Query(
        """
        select rl from ReviewLog rl
            where rl.studySession.id = :sessionId
        order by rl.id asc
    """,
    )
    fun findAllByStudySessionId(
        sessionId: Long,
    ): List<ReviewLog>
}
