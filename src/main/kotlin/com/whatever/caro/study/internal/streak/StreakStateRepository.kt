package com.whatever.caro.study.internal.streak

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface StreakStateRepository : JpaRepository<StreakState, Long> {
    fun findByUserId(
        userId: Long,
    ): StreakState?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
        """
        select ss from StreakState ss
        where ss.userId = :userId
        """,
    )
    fun findByUserIdForUpdate(
        userId: Long,
    ): StreakState?

    /**
     * 마지막 기록일이 [lastRecordedDate](= 유저 기준 어제)이고 streak이 이어지고 있는 상태.
     */
    @Query(
        """
        select ss from StreakState ss
        where ss.userId in :userIds
          and ss.lastRecordedDate = :lastRecordedDate
          and ss.currentStreak > 0
        """,
    )
    fun findAllAtRisk(
        userIds: Collection<Long>,
        lastRecordedDate: LocalDate,
    ): List<StreakState>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StreakState ss where ss.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
