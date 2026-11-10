package com.whatever.caro.notification.internal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface ReviewReminderSettingRepository : JpaRepository<ReviewReminderSetting, Long> {
    fun findByUserId(
        userId: Long,
    ): ReviewReminderSetting?

    /**
     * 활성화돼 있고 발송 시각이 지정한 시(hour) 집합에 드는 설정만 조회한다.
     * 스케줄러가 매시 실행될 때 "지금 어느 타임존에선가 현지 시각인 시(hour)"만 넘겨 대상을 좁힌다.
     */
    fun findAllByEnabledTrueAndReminderHourIn(
        reminderHours: Collection<Int>,
    ): List<ReviewReminderSetting>

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from ReviewReminderSetting s where s.userId = :userId")
    fun deleteByUserId(
        userId: Long,
    ): Int
}
