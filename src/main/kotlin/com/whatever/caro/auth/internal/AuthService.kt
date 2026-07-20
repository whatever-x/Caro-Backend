package com.whatever.caro.auth.internal

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.exception.InvalidRefreshTokenException
import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.auth.exception.WithdrawnException
import com.whatever.caro.auth.internal.config.JwtProperties
import com.whatever.caro.auth.internal.social.SocialIdTokenVerifierFactory
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.auth.internal.token.RefreshTokenRepository
import com.whatever.caro.auth.internal.token.TokenBlacklistRepository
import com.whatever.caro.auth.internal.web.request.CompleteRegistrationRequest
import com.whatever.caro.auth.internal.web.request.RefreshTokenRequest
import com.whatever.caro.auth.internal.web.request.SocialLoginRequest
import com.whatever.caro.auth.internal.web.response.SocialLoginResponse
import com.whatever.caro.auth.internal.web.response.TokenResponse
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service

private val logger = KotlinLogging.logger {}

@Service
class AuthService(
    private val socialIdTokenVerifierFactory: SocialIdTokenVerifierFactory,
    private val userApi: UserApi,
    private val jwtTokenProvider: JwtTokenProvider,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val jwtProperties: JwtProperties,
) {
    fun socialLogin(
        request: SocialLoginRequest,
        deviceId: String,
    ): SocialLoginResponse {
        val verifier = socialIdTokenVerifierFactory.getVerifier(request.provider)
        val socialUserInfo = try {
            verifier.verify(request.idToken)
        } catch (e: Exception) {
            throw InvalidSocialTokenException("소셜 로그인 처리 중 오류가 발생했습니다", e)
        }

        val userInfo = userApi.findBySocialProvider(
            provider = socialUserInfo.provider,
            providerUserId = socialUserInfo.providerUserId,
        ) ?: userApi.createSocialUser(
            provider = socialUserInfo.provider,
            providerUserId = socialUserInfo.providerUserId,
            email = socialUserInfo.email,
        )

        if (userInfo.isDeleted) throw WithdrawnException()

        val generated = jwtTokenProvider.generateAccessToken(
            userId = userInfo.id,
            status = userInfo.status.name,
        )
        val refreshToken = jwtTokenProvider.generateRefreshToken()

        refreshTokenRepository.save(
            userId = userInfo.id,
            deviceId = deviceId,
            refreshToken = refreshToken,
            accessTokenJti = generated.jti,
            expiresIn = jwtProperties.refreshTokenExpiresIn,
        )

        logger.info {
            "Social login successful: userId=${userInfo.id}, provider=${request.provider}, deviceId=$deviceId"
        }

        return SocialLoginResponse(
            accessToken = generated.token,
            refreshToken = refreshToken,
            isRegistrationComplete = userInfo.status == UserStatus.ACTIVE,
        )
    }

    fun completeRegistration(
        authUser: AuthUser,
        request: CompleteRegistrationRequest,
        deviceId: String,
    ): TokenResponse {
        val userInfo = userApi.completeRegistration(
            userId = authUser.userId,
            nickname = request.nickname,
            isTermsAgreed = request.isTermsAgreed,
        )

        tokenBlacklistRepository.add(
            jti = authUser.jti,
            expiresIn = jwtProperties.accessTokenExpiresIn,
        )

        val generated = jwtTokenProvider.generateAccessToken(
            userId = userInfo.id,
            status = userInfo.status.name,
        )
        val newRefreshToken = jwtTokenProvider.generateRefreshToken()
        refreshTokenRepository.save(
            userId = userInfo.id,
            deviceId = deviceId,
            refreshToken = newRefreshToken,
            accessTokenJti = generated.jti,
            expiresIn = jwtProperties.refreshTokenExpiresIn,
        )

        logger.info { "Registration completed: userId=${authUser.userId}, deviceId=$deviceId" }
        return TokenResponse(
            accessToken = generated.token,
            refreshToken = newRefreshToken,
        )
    }

    fun reissueToken(
        request: RefreshTokenRequest,
        deviceId: String,
    ): TokenResponse {
        val claims = jwtTokenProvider.parseAccessTokenAllowExpired(request.accessToken)

        val consumed = refreshTokenRepository.consumeToken(
            refreshToken = request.refreshToken,
            userId = claims.userId,
            deviceId = deviceId,
        ) ?: throw InvalidRefreshTokenException("유효하지 않은 Refresh Token입니다")

        tokenBlacklistRepository.add(consumed.accessTokenJti, jwtProperties.accessTokenExpiresIn)

        val userInfo = userApi.findById(claims.userId)
            ?: throw InvalidRefreshTokenException("사용자를 찾을 수 없습니다")

        if (userInfo.isDeleted) throw WithdrawnException()

        val generated = jwtTokenProvider.generateAccessToken(claims.userId, userInfo.status.name)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken()
        refreshTokenRepository.save(
            claims.userId,
            deviceId,
            newRefreshToken,
            generated.jti,
            jwtProperties.refreshTokenExpiresIn,
        )

        logger.info { "Token refreshed: userId=${claims.userId}, deviceId=$deviceId" }

        return TokenResponse(accessToken = generated.token, refreshToken = newRefreshToken)
    }

    fun logout(
        authUser: AuthUser,
        deviceId: String,
    ) {
        tokenBlacklistRepository.add(authUser.jti, jwtProperties.accessTokenExpiresIn)
        refreshTokenRepository.deleteByUserDevice(authUser.userId, deviceId)

        logger.info { "User logged out: userId=${authUser.userId}, deviceId=$deviceId" }
    }

    fun withdrawUser(
        authUser: AuthUser,
    ) {
        refreshTokenRepository.deleteAllByUserId(authUser.userId)
        tokenBlacklistRepository.add(authUser.jti, jwtProperties.accessTokenExpiresIn)
        userApi.deleteMe(authUser.userId)

        logger.info { "User withdraw : userId=${authUser.userId}" }
    }
}
