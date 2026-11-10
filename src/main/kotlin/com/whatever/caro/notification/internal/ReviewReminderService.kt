package com.whatever.caro.notification.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReviewReminderService(
    private val reviewReminderSettingRepository: ReviewReminderSettingRepository,
) {
    @Transactional(readOnly = true)
    fun getSetting(
        userId: Long,
    ): ReviewReminderSetting? = reviewReminderSettingRepository.findByUserId(userId = userId)

    /**
     * 복습 알림 설정을 upsert한다. 해제는 enabled=false로 저장한다(행은 유지).
     */
    @Transactional
    fun updateSetting(
        userId: Long,
        enabled: Boolean,
        reminderHour: Int,
    ) {
        val existing = reviewReminderSettingRepository.findByUserId(userId = userId)
        if (existing == null) {
            reviewReminderSettingRepository.save(
                ReviewReminderSetting(
                    userId = userId,
                    enabled = enabled,
                    reminderHour = reminderHour,
                ),
            )
            return
        }
        existing.update(enabled = enabled, reminderHour = reminderHour)
    }
}
