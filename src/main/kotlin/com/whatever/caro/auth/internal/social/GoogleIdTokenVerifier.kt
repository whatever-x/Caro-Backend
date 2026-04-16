package com.whatever.caro.auth.internal.social

import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.user.SocialProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Component
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier as GoogleVerifier

private val logger = KotlinLogging.logger {}

@Component
class GoogleIdTokenVerifier(
    private val googleVerifier: GoogleVerifier,
) : SocialIdTokenVerifier {
    override val provider: SocialProvider = SocialProvider.GOOGLE

    override fun verify(
        idToken: String,
    ): SocialUserInfo {
        val googleIdToken = runCatching {
            googleVerifier.verify(idToken)
        }.getOrElse { e ->
            logger.warn { "Google ID Token verification failed: ${e.message}" }
            throw InvalidSocialTokenException("Google ID Token 검증 실패", e)
        } ?: throw InvalidSocialTokenException("유효하지 않은 Google ID Token")

        val payload = googleIdToken.payload
        return SocialUserInfo(
            providerUserId = payload.subject,
            email = payload.email,
            provider = SocialProvider.GOOGLE,
        )
    }
}
