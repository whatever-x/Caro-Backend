package com.whatever.caro.notification.internal.web.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min

@Schema(name = "UpdateReviewReminderRequest", description = "복습 알림 설정 변경 요청")
data class UpdateReviewReminderRequest(
    @field:Schema(description = "복습 알림 수신 여부", example = "true", required = true)
    val enabled: Boolean,

    @field:Min(value = 0, message = "reminderHour must be between 0 and 23")
    @field:Max(value = 23, message = "reminderHour must be between 0 and 23")
    @field:Schema(description = "현지 시각 기준 발송 시(0~23)", example = "21", required = true)
    val reminderHour: Int,
)
