package com.whatever.caro.auth.internal.web.response

data class SocialLoginResponse(
    val accessToken: String,
    val refreshToken: String,
    val isRegistrationComplete: Boolean,
)
