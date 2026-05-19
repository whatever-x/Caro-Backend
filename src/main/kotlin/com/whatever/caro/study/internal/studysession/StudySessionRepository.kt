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
}
