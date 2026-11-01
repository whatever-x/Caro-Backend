package com.whatever.caro.notification.internal

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

/**
 * 유저별 복습 알림 설정. 유저당 1행(upsert).
 * 발송 시각 [reminderHour]는 유저 기기 타임존(DeviceToken.timezone) 기준의 현지 시(hour)다.
 * 타임존을 따로 저장하지 않아 기기 타임존이 바뀌면 발송 현지 시각도 함께 바뀐다.
 */
@Entity
@Table(name = "review_reminder_settings")
class ReviewReminderSetting(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,

    @Column(nullable = false)
    var enabled: Boolean,

    @Column(name = "reminder_hour", nullable = false)
    var reminderHour: Int,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    fun update(
        enabled: Boolean,
        reminderHour: Int,
    ) {
        this.enabled = enabled
        this.reminderHour = reminderHour
    }
}
