package com.whatever.caro.auth.internal.config

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTokenExpiresIn: Duration,
    val refreshTokenExpiresIn: Duration,
) {
    @PostConstruct
    fun validate() {
        require(secret.toByteArray().size >= 32) {
            "JWT secret must be at least 32 bytes. Current size: ${secret.toByteArray().size} bytes"
        }
        logger.info {
            "JwtProperties loaded: accessTokenExpireIn=$accessTokenExpiresIn, refreshTokenExpireIn=$refreshTokenExpiresIn"
        }
    }
}
