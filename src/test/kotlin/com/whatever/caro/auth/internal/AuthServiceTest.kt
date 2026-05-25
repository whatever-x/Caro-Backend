package com.whatever.caro.auth.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.exception.InvalidRefreshTokenException
import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.auth.internal.config.JwtProperties
import com.whatever.caro.auth.internal.social.SocialIdTokenVerifier
import com.whatever.caro.auth.internal.social.SocialIdTokenVerifierFactory
import com.whatever.caro.auth.internal.social.SocialUserInfo
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.auth.internal.web.request.CompleteRegistrationRequest
import com.whatever.caro.auth.internal.web.request.RefreshTokenRequest
import com.whatever.caro.auth.internal.web.request.SocialLoginRequest
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeEmpty
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Duration

private const val TEST_ID_TOKEN = "test-id-token"

/**
 * 소셜 로그인(Google/Apple)는 외부 OIDC 엔드포인트에 의존하므로 mock으로 대체
 *
 * Kotest DescribeSpec의 생성자 주입 방식에서는 @MockitoBean/@TestBean 사용이 제한적이라 @TestConfiguration + @Primary로 Bean을 교체한다.
 */
@TestConfiguration
class MockSocialVerifierConfig {

    @Bean
    @Primary
    fun socialIdTokenVerifierFactory(): SocialIdTokenVerifierFactory = mockk(relaxed = true)
}

@ApplicationModuleTest(extraIncludes = ["common", "user"])
@Import(TestcontainersConfiguration::class, MockSocialVerifierConfig::class)
class AuthServiceTest(
    private val authService: AuthService,
    private val socialIdTokenVerifierFactory: SocialIdTokenVerifierFactory,
    private val jwtTokenProvider: JwtTokenProvider,
    private val redisTemplate: StringRedisTemplate,
    private val userApi: UserApi,
) : DescribeSpec({

    val mockVerifier = mockk<SocialIdTokenVerifier>()

    afterEach {
        clearMocks(socialIdTokenVerifierFactory, mockVerifier, answers = false)
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    fun stubSocialVerifier(
        provider: SocialProvider = SocialProvider.GOOGLE,
        providerUserId: String = "google-uid",
        email: String? = "user@example.com",
    ) {
        every { socialIdTokenVerifierFactory.getVerifier(provider) } returns mockVerifier
        every { mockVerifier.verify(any()) } returns SocialUserInfo(
            providerUserId = providerUserId,
            email = email,
            provider = provider,
        )
    }

    describe("socialLogin") {
        it("신규 사용자 소셜 로그인 시 isRegistrationComplete=false이고 토큰이 발급된다") {
            stubSocialVerifier(providerUserId = "new-user-001")
            val deviceId = "device-1"
            val request = SocialLoginRequest(
                provider = SocialProvider.GOOGLE,
                idToken = TEST_ID_TOKEN,
            )

            val result = authService.socialLogin(request, deviceId)

            result.isRegistrationComplete shouldBe false
            result.accessToken.shouldNotBeEmpty()
            result.refreshToken.shouldNotBeEmpty()

            val claims = jwtTokenProvider.parseAccessToken(result.accessToken)
            claims.status shouldBe UserStatus.SUSPENDED.name
        }

        it("소셜 로그인 시 refresh token이 Redis에 저장된다") {
            stubSocialVerifier(providerUserId = "redis-check-user")
            val deviceId = "device-1"
            val request = SocialLoginRequest(
                provider = SocialProvider.GOOGLE,
                idToken = TEST_ID_TOKEN,
            )

            val result = authService.socialLogin(request, deviceId)
            val claims = jwtTokenProvider.parseAccessToken(result.accessToken)

            redisTemplate.opsForValue().get("refresh:${claims.userId}:$deviceId") shouldBe result.refreshToken
            redisTemplate.opsForValue().get("token_pair:${result.refreshToken}") shouldBe claims.jti
        }

        it("기존 SUSPENDED 사용자 재로그인 시 새 사용자를 생성하지 않는다") {
            stubSocialVerifier(providerUserId = "suspended-relogin-user")
            val deviceId = "device-1"

            // 1차 로그인 (신규 사용자 생성, SUSPENDED)
            val firstLogin = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val firstClaims = jwtTokenProvider.parseAccessToken(firstLogin.accessToken)

            // 2차 로그인 (가입 미완료 상태에서 재로그인)
            val secondLogin = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val secondClaims = jwtTokenProvider.parseAccessToken(secondLogin.accessToken)

            secondClaims.userId shouldBe firstClaims.userId // 같은 userId
            secondLogin.isRegistrationComplete shouldBe false
        }

        it("기존 ACTIVE 사용자 소셜 로그인 시 isRegistrationComplete=true") {
            stubSocialVerifier(providerUserId = "returning-user")
            val deviceId = "device-1"

            val firstLogin = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val firstClaims = jwtTokenProvider.parseAccessToken(firstLogin.accessToken)

            // 가입 완료 (SUSPENDED → ACTIVE)
            authService.completeRegistration(
                authUser = AuthUser(
                    userId = firstClaims.userId,
                    jti = firstClaims.jti,
                    status = firstClaims.status,
                ),
                request = CompleteRegistrationRequest(
                    nickname = "ReturningUser",
                    isTermsAgreed = true,
                ),
                deviceId = deviceId,
            )

            // 2차 로그인 (기존 ACTIVE 사용자)
            val secondLogin = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )

            secondLogin.isRegistrationComplete shouldBe true
        }

        it("ID 토큰 검증 실패 시 InvalidSocialTokenException을 던진다") {
            val deviceId = "device-1"
            every { socialIdTokenVerifierFactory.getVerifier(SocialProvider.GOOGLE) } returns mockVerifier
            every { mockVerifier.verify(any()) } throws RuntimeException("verification failed")

            shouldThrow<InvalidSocialTokenException> {
                authService.socialLogin(
                    SocialLoginRequest(
                        provider = SocialProvider.GOOGLE,
                        idToken = "invalid-token",
                    ),
                    deviceId,
                )
            }
        }
    }

    describe("completeRegistration") {
        it("등록 완료 시 새 access token에 ACTIVE status가 포함된다") {
            stubSocialVerifier(providerUserId = "reg-complete-user")
            val deviceId = "device-1"
            val loginResult = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val loginClaims = jwtTokenProvider.parseAccessToken(loginResult.accessToken)

            val nickname = "CompletedUser"
            val registrationResult = authService.completeRegistration(
                authUser = AuthUser(
                    userId = loginClaims.userId,
                    jti = loginClaims.jti,
                    status = loginClaims.status,
                ),
                request = CompleteRegistrationRequest(
                    nickname = nickname,
                    isTermsAgreed = true,
                ),
                deviceId = deviceId,
            )

            val newClaims = jwtTokenProvider.parseAccessToken(registrationResult.accessToken)
            newClaims.status shouldBe UserStatus.ACTIVE.name

            val savedUser = userApi.findById(loginClaims.userId)
            savedUser.shouldNotBeNull()
            savedUser.status shouldBe UserStatus.ACTIVE
            savedUser.nickname shouldBe nickname
            savedUser.isTermsAgreed shouldBe true
        }

        it("등록 완료 시 이전 access token JTI가 블랙리스트에 추가된다") {
            stubSocialVerifier(providerUserId = "blacklist-check-user")
            val deviceId = "device-1"
            val loginResult = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val loginClaims = jwtTokenProvider.parseAccessToken(loginResult.accessToken)

            val registrationResult = authService.completeRegistration(
                authUser = AuthUser(
                    userId = loginClaims.userId,
                    jti = loginClaims.jti,
                    status = loginClaims.status,
                ),
                request = CompleteRegistrationRequest(
                    nickname = "BlacklistUser",
                    isTermsAgreed = true,
                ),
                deviceId = deviceId,
            )
            val newClaims = jwtTokenProvider.parseAccessToken(registrationResult.accessToken)

            redisTemplate.hasKey("blacklist:${loginClaims.jti}") shouldBe true
            redisTemplate.hasKey("blacklist:${newClaims.jti}") shouldBe false
        }
    }

    describe("reissueToken") {
        it("정상 토큰 갱신 시 새 토큰이 발급되고 이전 JTI가 블랙리스트에 추가된다") {
            stubSocialVerifier(providerUserId = "reissue-user")
            val deviceId = "device-1"
            val loginResult = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val loginClaims = jwtTokenProvider.parseAccessToken(loginResult.accessToken)

            val reissueResult = authService.reissueToken(
                RefreshTokenRequest(
                    refreshToken = loginResult.refreshToken,
                    accessToken = loginResult.accessToken,
                ),
                deviceId,
            )
            val newClaims = jwtTokenProvider.parseAccessToken(reissueResult.accessToken)

            reissueResult.accessToken.shouldNotBeEmpty()
            reissueResult.refreshToken.shouldNotBeEmpty()
            redisTemplate.hasKey("blacklist:${loginClaims.jti}") shouldBe true
            redisTemplate.hasKey("token_pair:${loginResult.refreshToken}") shouldBe false

            redisTemplate.hasKey("blacklist:${newClaims.jti}") shouldBe false
            redisTemplate.hasKey("token_pair:${reissueResult.refreshToken}") shouldBe true
        }

        it("유효하지 않은 refresh token 시 예외를 던진다") {
            stubSocialVerifier(providerUserId = "invalid-refresh-user")
            val deviceId = "device-1"
            val loginResult = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )

            shouldThrow<InvalidRefreshTokenException> {
                authService.reissueToken(
                    RefreshTokenRequest(
                        refreshToken = "wrong-refresh-token",
                        accessToken = loginResult.accessToken,
                    ),
                    deviceId,
                )
            }
        }

        it("잘못된 서명의 access token 시 예외를 던진다") {
            val wrongProperties = JwtProperties(
                secret = "wrong-secret-key-that-is-at-least-32-bytes!",
                accessTokenExpiresIn = Duration.ofHours(1),
                refreshTokenExpiresIn = Duration.ofDays(30),
            )
            val wrongProvider = JwtTokenProvider(wrongProperties, java.time.Clock.systemUTC()).apply { init() }
            val wrongToken = wrongProvider.generateAccessToken(1L, UserStatus.ACTIVE.name)

            val deviceId = "device-1"
            shouldThrow<io.jsonwebtoken.security.SignatureException> {
                authService.reissueToken(
                    RefreshTokenRequest(
                        refreshToken = "any-refresh",
                        accessToken = wrongToken.token,
                    ),
                    deviceId,
                )
            }
        }
    }

    describe("logout") {
        it("로그아웃 시 access token이 블랙리스트에 추가되고 refresh token이 삭제된다") {
            stubSocialVerifier(providerUserId = "logout-user")
            val deviceId = "device-1"
            val loginResult = authService.socialLogin(
                SocialLoginRequest(
                    provider = SocialProvider.GOOGLE,
                    idToken = TEST_ID_TOKEN,
                ),
                deviceId,
            )
            val loginClaims = jwtTokenProvider.parseAccessToken(loginResult.accessToken)

            authService.logout(
                authUser = AuthUser(
                    userId = loginClaims.userId,
                    jti = loginClaims.jti,
                    status = loginClaims.status,
                ),
                deviceId = deviceId,
            )

            redisTemplate.hasKey("blacklist:${loginClaims.jti}") shouldBe true
            redisTemplate.hasKey("refresh:${loginClaims.userId}:$deviceId") shouldBe false
            redisTemplate.hasKey("token_pair:${loginResult.refreshToken}") shouldBe false
        }
    }
})
