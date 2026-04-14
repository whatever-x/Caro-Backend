package com.whatever.caro.auth.internal.web.request

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "Refresh Token은 필수입니다")
    val refreshToken: String,

    @field:NotBlank(message = "Device ID는 필수입니다")
    val deviceId: String,

    @field:NotBlank(message = "Access Token은 필수입니다")
    val accessToken: String,
)
