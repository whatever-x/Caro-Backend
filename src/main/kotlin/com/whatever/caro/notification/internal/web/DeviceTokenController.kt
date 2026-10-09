package com.whatever.caro.notification.internal.web

import com.whatever.caro.notification.internal.NotificationService
import com.whatever.caro.notification.internal.web.request.RegisterDeviceTokenRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZoneId
import java.util.Locale

@Tag(name = "Notification", description = "푸시 알림 기기 토큰 관리")
@Validated
@RestController
@RequestMapping("/notifications/devices")
class DeviceTokenController(
    private val notificationService: NotificationService,
) {

    @Operation(
        summary = "기기 토큰 등록",
        description = """
        FCM 토큰과 기기 타임존(Client-Timezone), 언어(Accept-Language)를 등록(upsert)한다.
        앱 실행 시와 토큰 갱신(onNewToken) 시 호출한다.
        """,
    )
    @PutMapping(version = "1.0")
    fun registerToken(
        @Valid @RequestBody request: RegisterDeviceTokenRequest,
        @RequestHeader("Client-Timezone") timezone: ZoneId,
        locale: Locale,
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<Void> {
        notificationService.registerToken(
            userId = userId,
            token = request.token,
            platform = request.platform,
            timezone = timezone,
            locale = locale,
        )
        return ResponseEntity.noContent().build()
    }

    @Operation(
        summary = "기기 토큰 삭제",
        description = "로그아웃 시 해당 기기의 토큰을 삭제한다. 없는 토큰이어도 204를 반환한다.",
    )
    @DeleteMapping(version = "1.0")
    fun unregisterToken(
        @RequestParam
        @NotBlank(message = "Token is required")
        token: String,
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<Void> {
        notificationService.unregisterToken(userId = userId, token = token)
        return ResponseEntity.noContent().build()
    }
}
