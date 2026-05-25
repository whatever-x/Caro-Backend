package com.whatever.caro.auth.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "TokenResponse", description = "토큰 발급/재발급 응답")
data class TokenResponse(
    @field:Schema(description = "JWT Access Token", example = "<jwt-access-token>")
    val accessToken: String,
    @field:Schema(description = "JWT Refresh Token", example = "<jwt-refresh-token>")
    val refreshToken: String,
)
