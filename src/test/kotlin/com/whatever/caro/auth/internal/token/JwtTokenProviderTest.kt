package com.whatever.caro.auth.internal.token

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.auth.internal.config.JwtProperties
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

class JwtTokenProviderTest :
    DescribeSpec({

        val secret = "this-is-a-test-secret-key-at-least-32-bytes-long"
        val fixedInstant = Instant.parse("2025-01-01T00:00:00Z")
        val fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
        val jwtProperties = JwtProperties(
            secret = secret,
            accessTokenExpiresIn = Duration.ofHours(1),
            refreshTokenExpiresIn = Duration.ofDays(30),
        )

        val jwtTokenProvider = JwtTokenProvider(jwtProperties, fixedClock).apply { init() }

        describe("generateAccessToken") {
            it("GeneratedAccessToken을 반환한다") {
                val userId = 0L
                val status = "ACTIVE"
                val generated = jwtTokenProvider.generateAccessToken(userId, status)

                generated.token.shouldNotBeEmpty()
                generated.jti.shouldNotBeEmpty()

                val claims = jwtTokenProvider.parseAccessToken(generated.token)
                claims.userId shouldBe userId
                claims.status shouldBe status
                claims.jti shouldBe generated.jti
            }
        }

        describe("generateRefreshToken") {
            it("UUID 형식의 Refresh 토큰을 생성한다") {
                val refreshToken = jwtTokenProvider.generateRefreshToken()

                UUID.fromString(refreshToken) // UUID 파싱 가능해야 함
            }
        }

        describe("parseAccessToken") {
            val userId = 0L
            val status = "ACTIVE"
            it("토큰에서 userId, jti, status를 추출한다") {
                val generated = jwtTokenProvider.generateAccessToken(userId, status)

                val claims = jwtTokenProvider.parseAccessToken(generated.token)

                claims.userId shouldBe userId
                claims.status shouldBe status
                claims.jti shouldBe generated.jti
            }

            it("만료된 토큰 파싱 시 ExpiredJwtException을 던진다") {
                val targetInstant = fixedInstant
                    .minus(Duration.ofHours(1))
                    .minus(Duration.ofMillis(1))
                val pastClock = Clock.fixed(
                    targetInstant,
                    ZoneOffset.UTC,
                )
                val pastProvider = JwtTokenProvider(jwtProperties, pastClock).apply { init() }
                val expiredGenerated = pastProvider.generateAccessToken(
                    userId = userId,
                    status = status,
                    expiresAfter = Duration.ofHours(1),
                )

                // 토큰 발급: 22:59:59.999, 만료: 23:59:59.999 (발급시간 +1h)
                // 파싱 시각: 2025-01-01T00:00:00 -> 만료 시각보다 1ms 후이므로 ExpiredJwtException 발생
                shouldThrow<ExpiredJwtException> {
                    jwtTokenProvider.parseAccessToken(expiredGenerated.token)
                }
            }

            it("잘못된 서명 토큰 파싱 시 SignatureException을 던진다") {
                val wrongSecret = "wrong-secret-key-that-is-also-at-least-32-bytes!"
                val wrongProperties = JwtProperties(
                    secret = wrongSecret,
                    accessTokenExpiresIn = Duration.ofHours(1),
                    refreshTokenExpiresIn = Duration.ofDays(30),
                )
                val wrongProvider = JwtTokenProvider(wrongProperties, fixedClock).apply { init() }
                val wrongGenerated = wrongProvider.generateAccessToken(userId, status)

                shouldThrow<SignatureException> {
                    jwtTokenProvider.parseAccessToken(wrongGenerated.token)
                }
            }

            it("status claim이 없으면 InvalidAccessTokenException을 던진다") {
                val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())
                val tokenWithoutStatus = Jwts.builder()
                    .subject("1")
                    .id("test-jti")
                    .issuedAt(Date.from(fixedInstant))
                    .expiration(Date.from(fixedInstant.plus(Duration.ofHours(1))))
                    .signWith(secretKey)
                    .compact()

                shouldThrow<InvalidAccessTokenException> {
                    jwtTokenProvider.parseAccessToken(tokenWithoutStatus)
                }
            }

            it("subject가 숫자가 아니면 InvalidAccessTokenException을 던진다") {
                val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())
                val tokenWithInvalidSubject = Jwts.builder()
                    .subject("not-a-number")
                    .id("test-jti")
                    .claim("status", "ACTIVE")
                    .issuedAt(Date.from(fixedInstant))
                    .expiration(Date.from(fixedInstant.plus(Duration.ofHours(1))))
                    .signWith(secretKey)
                    .compact()

                shouldThrow<InvalidAccessTokenException> {
                    jwtTokenProvider.parseAccessToken(tokenWithInvalidSubject)
                }
            }

            it("jti가 없으면 InvalidAccessTokenException을 던진다") {
                val secretKey = Keys.hmacShaKeyFor(secret.toByteArray())
                val tokenWithoutJti = Jwts.builder().subject("1").claim("status", "ACTIVE").issuedAt(Date.from(fixedInstant)).expiration(Date.from(fixedInstant.plus(Duration.ofHours(1)))).signWith(secretKey).compact()

                shouldThrow<InvalidAccessTokenException> {
                    jwtTokenProvider.parseAccessToken(tokenWithoutJti)
                }
            }
        }

        describe("parseAccessTokenAllowExpired") {
            val userId = 0L
            val status = "ACTIVE"

            it("만료된 토큰을 정상 파싱한다") {
                val targetInstant = fixedInstant
                    .minus(Duration.ofHours(1))
                    .minus(Duration.ofMillis(1))
                val pastClock = Clock.fixed(targetInstant, ZoneOffset.UTC)
                val pastProvider = JwtTokenProvider(jwtProperties, pastClock).apply { init() }
                val expiredAccessToken = pastProvider.generateAccessToken(userId, status, Duration.ofHours(1))

                val claims = jwtTokenProvider.parseAccessTokenAllowExpired(expiredAccessToken.token)

                claims.userId shouldBe userId
                claims.status shouldBe status
                claims.jti shouldBe expiredAccessToken.jti
            }

            it("잘못된 서명의 만료 토큰은 SignatureException을 던진다") {
                val wrongSecret = "wrong-secret-key-that-is-also-at-least-32-bytes!"
                val wrongProperties = JwtProperties(
                    secret = wrongSecret,
                    accessTokenExpiresIn = Duration.ofHours(1),
                    refreshTokenExpiresIn = Duration.ofDays(30),
                )
                val wrongProvider = JwtTokenProvider(wrongProperties, fixedClock).apply { init() }
                val wrongGenerated = wrongProvider.generateAccessToken(userId, status)

                shouldThrow<SignatureException> {
                    jwtTokenProvider.parseAccessTokenAllowExpired(wrongGenerated.token)
                }
            }
        }
    })
