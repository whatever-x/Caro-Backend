package com.whatever.caro.auth.internal.social

import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.user.SocialProvider
import org.springframework.stereotype.Component

@Component
class SocialIdTokenVerifierFactory(
    verifiers: List<SocialIdTokenVerifier>,
) {
    private val verifierMap: Map<SocialProvider, SocialIdTokenVerifier> = verifiers.associateBy { it.provider }

    fun getVerifier(
        provider: SocialProvider,
    ): SocialIdTokenVerifier =
        verifierMap[provider]
            ?: throw InvalidSocialTokenException("지원하지 않는 소셜 로그인 제공자입니다: $provider")
}
