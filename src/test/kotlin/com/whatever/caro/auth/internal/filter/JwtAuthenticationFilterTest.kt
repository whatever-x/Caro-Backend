package com.whatever.caro.auth.internal.filter

import com.whatever.caro.auth.exception.InvalidAccessTokenException
import com.whatever.caro.auth.internal.config.PublicEndpoints
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.auth.internal.token.TokenBlacklistRepository
import com.whatever.caro.auth.internal.token.TokenClaims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class JwtAuthenticationFilterTest :
    DescribeSpec({

        val jwtTokenProvider = mockk<JwtTokenProvider>()
        val tokenBlacklistRepository = mockk<TokenBlacklistRepository>()
        val filterChain = mockk<FilterChain>(relaxed = true)

        val filter = JwtAuthenticationFilter(jwtTokenProvider, tokenBlacklistRepository)

        afterTest {
            SecurityContextHolder.clearContext()
            clearAllMocks(answers = false)
        }

        describe("shouldNotFilter") {
            val invalidToken = "invalid.token.on.public.path"
            fun stubInvalidToken() {
                every { jwtTokenProvider.parseAccessToken(invalidToken) } throws JwtException("Invalid signature")
            }

            val expectedPublicAuthPaths = listOf("/auth/social-login", "/auth/refresh")
            it("public api 목록은 이 테스트의 기대값과 같아야한다") {
                PublicEndpoints.AUTH shouldContainExactlyInAnyOrder expectedPublicAuthPaths
            }

            context("공개 경로는 토큰을 검사하지 않고 통과한다") {
                withData(
                    expectedPublicAuthPaths +
                        listOf("/actuator/health", "/swagger", "/swagger-ui/index.html", "/v3/api-docs/version-1", "/v3/api-docs.yaml"),
                ) { uri ->
                    stubInvalidToken()
                    val request = MockHttpServletRequest().apply {
                        requestURI = uri
                        addHeader("Authorization", "Bearer $invalidToken")
                    }
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    SecurityContextHolder.getContext().authentication shouldBe null
                    verify { filterChain.doFilter(request, response) }
                    verify(exactly = 0) { jwtTokenProvider.parseAccessToken(any()) }
                }
            }

            context("공개 경로가 아니면 같은 토큰으로 예외가 난다") {
                withData("/decks", "/auth/logout", "/api/v1/auth/social-login") { uri ->
                    stubInvalidToken()
                    val request = MockHttpServletRequest().apply {
                        requestURI = uri
                        addHeader("Authorization", "Bearer $invalidToken")
                    }
                    val response = MockHttpServletResponse()

                    shouldThrow<InvalidAccessTokenException> {
                        filter.doFilter(request, response, filterChain)
                    }
                    verify(exactly = 0) { filterChain.doFilter(any(), any()) }
                }
            }
        }

        describe("doFilterInternal") {
            context("유효한 Bearer 토큰이 있을 때") {
                it("SecurityContext에 Authentication이 설정된다") {
                    val token = "valid.jwt.token"
                    val claims = TokenClaims(userId = 1L, jti = "test-jti", status = "ACTIVE")
                    every { jwtTokenProvider.parseAccessToken(token) } returns claims
                    every { tokenBlacklistRepository.isBlacklisted("test-jti") } returns false

                    val request = MockHttpServletRequest().apply {
                        requestURI = "/test/uri"
                        addHeader("Authorization", "Bearer $token")
                    }
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    val authentication = SecurityContextHolder.getContext().authentication
                    authentication.shouldNotBeNull()
                    authentication.isAuthenticated shouldBe true
                    verify { filterChain.doFilter(request, response) }
                    verify(exactly = 1) { jwtTokenProvider.parseAccessToken(any()) }
                }
            }

            context("Invalid한 Bearer 토큰이 있을 때") {
                it("Bearer 접두사가 없으면 인증 없이 통과한다") {
                    val request = MockHttpServletRequest().apply {
                        requestURI = "/test/uri"
                        addHeader("Authorization", "Basic some-credentials")
                    }
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    SecurityContextHolder.getContext().authentication shouldBe null
                    verify { filterChain.doFilter(request, response) }
                    verify(exactly = 0) { jwtTokenProvider.parseAccessToken(any()) }
                }

                it("서명이 유효하지 않은 토큰이면 InvalidAccessTokenException을 던진다") {
                    val token = "invalid.signature.token"
                    every { jwtTokenProvider.parseAccessToken(token) } throws JwtException("Invalid signature")

                    val request = MockHttpServletRequest().apply {
                        requestURI = "/test/uri"
                        addHeader("Authorization", "Bearer $token")
                    }
                    val response = MockHttpServletResponse()

                    shouldThrow<InvalidAccessTokenException> {
                        filter.doFilter(request, response, filterChain)
                    }
                }

                it("Bearer 접두사만 존재한다면 인증 없이 통과한다") {
                    val blankToken = "  "

                    val request = MockHttpServletRequest().apply {
                        requestURI = "/test/uri"
                        addHeader("Authorization", "Bearer $blankToken")
                    }
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    SecurityContextHolder.getContext().authentication shouldBe null
                    verify { filterChain.doFilter(request, response) }
                    verify(exactly = 0) { jwtTokenProvider.parseAccessToken(any()) }
                }
            }

            context("Authorization 헤더가 없을 때") {
                it("SecurityContext가 비어있고 filterChain이 계속 진행된다") {
                    val request = MockHttpServletRequest()
                    val response = MockHttpServletResponse()

                    filter.doFilter(request, response, filterChain)

                    val authentication = SecurityContextHolder.getContext().authentication
                    authentication shouldBe null
                    verify { filterChain.doFilter(request, response) }
                }
            }

            context("만료된 토큰일 때") {
                it("InvalidAccessTokenException을 던진다") {
                    val token = "expired.jwt.token"
                    every { jwtTokenProvider.parseAccessToken(token) } throws mockk<ExpiredJwtException>(relaxed = true)

                    val request = MockHttpServletRequest().apply {
                        requestURI = "/test/uri"
                        addHeader("Authorization", "Bearer $token")
                    }
                    val response = MockHttpServletResponse()

                    shouldThrow<InvalidAccessTokenException> {
                        filter.doFilter(request, response, filterChain)
                    }
                }
            }

            context("블랙리스트에 있는 토큰일 때") {
                it("InvalidAccessTokenException을 던진다") {
                    val token = "blacklisted.jwt.token"
                    val claims = TokenClaims(userId = 2L, jti = "blacklisted-jti", status = "ACTIVE")

                    every { jwtTokenProvider.parseAccessToken(token) } returns claims
                    every { tokenBlacklistRepository.isBlacklisted("blacklisted-jti") } returns true

                    val request = MockHttpServletRequest().apply {
                        addHeader("Authorization", "Bearer $token")
                    }
                    val response = MockHttpServletResponse()

                    shouldThrow<InvalidAccessTokenException> {
                        filter.doFilter(request, response, filterChain)
                    }
                }
            }
        }
    })
