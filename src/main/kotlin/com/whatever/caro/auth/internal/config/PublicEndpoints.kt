package com.whatever.caro.auth.internal.config

object PublicEndpoints {
    val PATTERNS = listOf(
        "/api/v1/auth/social-login",
        "/api/v1/auth/refresh",
        "/actuator/**",
    )
}
