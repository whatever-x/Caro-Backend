package com.whatever.caro.auth.internal.config

object PublicEndpoints {
    val SWAGGER = listOf(
        "/swagger",
        "/swagger-ui/**",
        "/v3/api-docs/**",
        "/v3/api-docs.yaml",
    )

    val MONITORING = listOf(
        "/actuator/**",
    )

    val AUTH = listOf(
        "/auth/social-login",
        "/auth/refresh",
    )

    val PATTERNS: List<String> = AUTH + MONITORING + SWAGGER
}
