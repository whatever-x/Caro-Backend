package com.whatever.caro.study.internal.streak

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

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
}
