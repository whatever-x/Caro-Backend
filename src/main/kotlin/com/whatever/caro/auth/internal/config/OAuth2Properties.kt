package com.whatever.caro.auth.internal.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.oauth2")
data class OAuth2Properties(
    val google: GoogleProperties,
    val apple: AppleProperties,
) {
    data class GoogleProperties(
        val clientId: String,
    )

    data class AppleProperties(
        val clientIds: Set<String>,
        val jwksUri: String,
    )
}
