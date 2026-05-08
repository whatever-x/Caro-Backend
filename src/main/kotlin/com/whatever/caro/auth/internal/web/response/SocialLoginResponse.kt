package com.whatever.caro.auth.internal.web.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "SocialLoginResponse", description = "소셜 로그인 응답")
data class SocialLoginResponse(
    @field:Schema(description = "JWT Access Token", example = "<jwt-access-token>")
    val accessToken: String,
    @field:Schema(description = "JWT Refresh Token", example = "<jwt-refresh-token>")
    val refreshToken: String,
    @field:Schema(description = "신규 가입자는 false (회원가입 완료 필요), 기존 회원은 true", example = "false")
    val isRegistrationComplete: Boolean,
)
