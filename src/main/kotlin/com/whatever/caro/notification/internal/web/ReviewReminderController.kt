package com.whatever.caro.notification.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.notification.internal.ReviewReminderService
import com.whatever.caro.notification.internal.web.request.UpdateReviewReminderRequest
import com.whatever.caro.notification.internal.web.response.ReviewReminderResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Notification", description = "복습 알림 설정")
@Validated
@RestController
@RequestMapping("/notifications/review-reminder")
class ReviewReminderController(
    private val reviewReminderService: ReviewReminderService,
) {

    @Operation(
        summary = "복습 알림 설정 조회",
        description = "설정이 없으면 수신 꺼짐과 기본 시각을 반환한다.",
    )
    @GetMapping(version = "1.0")
    fun getReviewReminder(
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<ApiResponse<ReviewReminderResponse>> {
        val setting = reviewReminderService.getSetting(userId = userId)
        return ResponseEntity.ok(ApiResponse.ok(ReviewReminderResponse.of(setting)))
    }

    @Operation(
        summary = "복습 알림 설정 변경",
        description = "수신 여부와 발송 시각을 upsert한다. 해제는 enabled=false로 보낸다.",
    )
    @PutMapping(version = "1.0")
    fun updateReviewReminder(
        @Valid @RequestBody request: UpdateReviewReminderRequest,
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<Void> {
        reviewReminderService.updateSetting(
            userId = userId,
            enabled = request.enabled,
            reminderHour = request.reminderHour,
        )
        return ResponseEntity.noContent().build()
    }
}
