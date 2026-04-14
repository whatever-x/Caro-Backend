package com.whatever.caro.auth.internal.token

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.auth.internal.config.JwtProperties
import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import jakarta.annotation.PostConstruct
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

private val logger = KotlinLogging.logger {}

data class TokenClaims(
    val userId: Long,
    val jti: String,
    val status: String,
)

data class GeneratedAccessToken(
    val token: String,
    val jti: String,
)

@Component
class JwtTokenProvider(
    private val jwtProperties: JwtProperties,
    private val clock: Clock,
) {
    private lateinit var secretKey: SecretKey

    @PostConstruct
    fun init() {
        secretKey = Keys.hmacShaKeyFor(jwtProperties.secret.toByteArray())
        logger.info { "JwtTokenProvider initialized" }
    }

    fun generateAccessToken(
        userId: Long,
        status: String,
        expiresAfter: Duration = jwtProperties.accessTokenExpiresIn,
    ): GeneratedAccessToken {
        val now = Instant.now(clock)

        val sub = userId.toString()
        val jti = UUID.randomUUID().toString()
        val issuedAt = Date.from(now)
        val expiredAt = Date.from(now.plus(expiresAfter))
        val token = Jwts.builder()
            .subject(sub)
            .id(jti)
            .claim(CLAIM_STATUS, status)
            .issuedAt(issuedAt)
            .expiration(expiredAt)
            .signWith(secretKey)
            .compact()
        return GeneratedAccessToken(token = token, jti = jti)
    }

    fun generateRefreshToken(): String = UUID.randomUUID().toString()

    fun parseAccessToken(
        token: String,
    ): TokenClaims {
        val claims = Jwts.parser()
            .verifyWith(secretKey)
            .clock { Date.from(Instant.now(clock)) }
            .build()
            .parseSignedClaims(token)
            .payload

        return extractClaims(claims)
    }

    fun parseAccessTokenAllowExpired(
        token: String,
    ): TokenClaims =
        try {
            parseAccessToken(token)
        } catch (e: ExpiredJwtException) {
            extractClaims(e.claims)
        }

    private fun extractClaims(
        claims: Claims,
    ): TokenClaims {
        val userId = claims.subject.toLongOrNull()
            ?: throw InvalidAccessTokenException("토큰에 사용자 정보가 없습니다")
        val jti = claims.id
            ?: throw InvalidAccessTokenException("토큰에 JTI 정보가 없습니다")
        val status = claims.get(CLAIM_STATUS, String::class.java)
            ?: throw InvalidAccessTokenException("토큰에 status 정보가 없습니다")

        return TokenClaims(
            userId = userId,
            jti = jti,
            status = status,
        )
    }

    companion object {
        private const val CLAIM_STATUS = "status"
    }
}
