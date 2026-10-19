package com.whatever.caro.notification.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.notification.internal.NotificationService
import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.notification.internal.web.response.TestPushResponse
import com.whatever.caro.notification.internal.web.response.toTestPushResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * FCM 연동 확인용. prod에서는 빈이 등록되지 않는다.
 */
@Profile("local", "staging")
@Tag(name = "Notification", description = "푸시 알림 기기 토큰 관리")
@RestController
@RequestMapping("/notifications")
class TestPushController(
    private val notificationService: NotificationService,
) {

    @Operation(
        summary = "[테스트] 내 기기로 푸시 발송",
        description = "로그인한 사용자의 모든 등록 기기로 테스트 푸시를 보낸다. local/staging 전용.",
    )
    @PostMapping("/test", version = "1.0")
    fun sendTestPush(
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<ApiResponse<TestPushResponse>> {
        val result = notificationService.sendToUser(
            userId = userId,
            message = PushMessage(
                title = "Caro 테스트 알림",
                body = "푸시 연동이 정상 동작합니다 🎉",
                data = mapOf("type" to "TEST"),
            ),
        )
        return ResponseEntity.ok(ApiResponse.ok(result.toTestPushResponse()))
    }
}
