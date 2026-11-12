package com.whatever.caro.notification.internal.web.request

import com.whatever.caro.notification.internal.DevicePlatform
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Schema(name = "RegisterDeviceTokenRequest", description = "푸시 기기 토큰 등록 요청")
data class RegisterDeviceTokenRequest(
    @field:NotBlank(message = "{validation.device_token.required}")
    @field:Size(max = 512, message = "{validation.device_token.max_size}")
    @field:Schema(description = "FCM registration token", required = true)
    val token: String,

    @field:Schema(description = "기기 플랫폼", example = "ANDROID", required = true)
    val platform: DevicePlatform,
)
