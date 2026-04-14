package com.whatever.caro.auth.internal.web.request

import jakarta.validation.constraints.NotBlank

data class LogoutRequest(
    @field:NotBlank(message = "Device ID는 필수입니다")
    val deviceId: String,
)
