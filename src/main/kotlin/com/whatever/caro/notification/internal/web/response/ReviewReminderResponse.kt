package com.whatever.caro.notification.internal.web.response

import com.whatever.caro.notification.internal.ReviewReminderSetting
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "ReviewReminderResponse", description = "복습 알림 설정")
data class ReviewReminderResponse(
    @field:Schema(description = "복습 알림 수신 여부")
    val enabled: Boolean,

    @field:Schema(description = "현지 시각 기준 발송 시(0~23)")
    val reminderHour: Int,
) {
    companion object {
        /** 설정이 없는 유저의 기본값. 저녁 시간대를 기본 제안하되 수신은 꺼진 상태. */
        const val DEFAULT_REMINDER_HOUR = 21

        fun of(
            setting: ReviewReminderSetting?,
        ): ReviewReminderResponse =
            if (setting == null) {
                ReviewReminderResponse(enabled = false, reminderHour = DEFAULT_REMINDER_HOUR)
            } else {
                ReviewReminderResponse(enabled = setting.enabled, reminderHour = setting.reminderHour)
            }
    }
}
