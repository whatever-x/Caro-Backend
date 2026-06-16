package com.whatever.caro.study.internal.studysession

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface StudySessionRepository : JpaRepository<StudySession, Long> {

    @Query(
        """
        select ss from StudySession ss
        where ss.userId = :userId
          and ss.deckId = :deckId
        order by ss.startedAt desc limit 1
    """,
    )
    fun findByUserAndDeckOrderByStartedAtDesc(
        userId: Long,
        deckId: Long,
    ): StudySession?

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update StudySession ss set ss.status = StudySessionStatus.STOPPED
        where ss.id = :id
          and ss.status = StudySessionStatus.ACTIVE
    """,
    )
    fun setStoppedIfActive(
        id: Long,
    ): Int

    fun findByIdAndUserId(
        id: Long,
        userId: Long,
    ): StudySession?

    @Query(
        """
        select ss from StudySession ss
        where ss.userId = :userId
            and ss.deckId in :deckIds
            and not exists (
                select 1 from StudySession ss2
                where ss2.userId = :userId
                    and ss2.deckId = ss.deckId
                    and (ss2.startedAt > ss.startedAt
                            or (ss2.startedAt = ss.startedAt and ss2.id > ss.id))
            )
        """,
    )
    fun findLatestByUserIdAndDeckIdIn(
        userId: Long,
        deckIds: Set<Long>,
    ): List<StudySession>
}
