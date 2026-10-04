package com.whatever.caro.notification.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(
        token: String,
    ): DeviceToken?

    fun findAllByUserId(
        userId: Long,
    ): List<DeviceToken>

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeviceToken d where d.userId = :userId and d.token = :token")
    fun deleteByUserIdAndToken(
        userId: Long,
        token: String,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeviceToken d where d.token in :tokens")
    fun deleteAllByTokenIn(
        tokens: Collection<String>,
    ): Int

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from DeviceToken d where d.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
