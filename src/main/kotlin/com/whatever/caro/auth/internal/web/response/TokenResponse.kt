package com.whatever.caro.auth.internal.web.response

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
)
