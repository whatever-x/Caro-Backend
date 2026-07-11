package com.whatever.caro.study.internal.studysession

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

interface StudySessionRepository : JpaRepository<StudySession, Long> {

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): StudySession?

    @Query(
        """
        select ss from StudySession ss
        where ss.userId = :userId
            and ss.deckId = :deckId
            and ss.sessionDate between :fromDate and :toDate
    """,
    )
    fun findByUserIdAndDeckIdAndSessionDateBetween(
        userId: Long,
        deckId: Long,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<StudySession>

    @Query(
        """
        select ss from StudySession ss
        where ss.userId = :userId
            and ss.deckId in :deckIds
            and ss.sessionDate between :fromDate and :toDate
        """,
    )
    fun findByUserIdAndDeckIdInAndSessionDateBetween(
        userId: Long,
        deckIds: Set<Long>,
        fromDate: LocalDate,
        toDate: LocalDate,
    ): List<StudySession>

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update StudySession ss set ss.status = StudySessionStatus.STOPPED
        where ss.status = StudySessionStatus.ACTIVE
            and ss.sessionDate <= :before
    """,
    )
    fun stopStaledActiveBefore(
        before: LocalDate,
    ): Int
}
