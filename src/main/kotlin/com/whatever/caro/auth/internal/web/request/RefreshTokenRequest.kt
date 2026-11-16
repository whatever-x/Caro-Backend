package com.whatever.caro.auth.internal.web.request

import jakarta.validation.constraints.NotBlank

data class RefreshTokenRequest(
    @field:NotBlank(message = "{validation.refresh_token.required}")
    val refreshToken: String,

    @field:NotBlank(message = "{validation.access_token.required}")
    val accessToken: String,
)
