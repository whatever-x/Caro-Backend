package com.whatever.caro.auth.internal.social

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.proc.SecurityContext
import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.auth.internal.config.OAuth2Properties
import com.whatever.caro.user.SocialProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jwt.JwtClaimNames
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

private val logger = KotlinLogging.logger {}

@Component
class AppleIdTokenVerifier(
    private val oauth2Properties: OAuth2Properties,
    private val oidcPublicKeyCacheRepository: OidcPublicKeyCacheRepository,
    restClientBuilder: RestClient.Builder,
) : SocialIdTokenVerifier {
    override val provider: SocialProvider = SocialProvider.APPLE

    private val restClient = restClientBuilder.baseUrl(oauth2Properties.apple.jwksUri).build()

    @Volatile
    private var cachedDecoder: NimbusJwtDecoder? = null

    override fun verify(
        idToken: String,
    ): SocialUserInfo {
        val decoder = cachedDecoder ?: buildDecoder().also { cachedDecoder = it }

        val jwt = runCatching {
            decoder.decode(idToken)
        }.recoverCatching { e ->
            if (isSignatureFailure(e)) {
                logger.info { "Apple JWKS kid mismatch, refreshing cache" }
                oidcPublicKeyCacheRepository.evict(PROVIDER_KEY)

                val newDecoder = buildDecoder()
                cachedDecoder = newDecoder
                newDecoder.decode(idToken)
            } else {
                throw e
            }
        }.getOrElse { e ->
            logger.warn { "Apple ID Token verification failed: ${e.message}" }
            throw InvalidSocialTokenException("Apple ID Token 검증 실패", e)
        }

        val providerUserId = jwt.subject ?: throw InvalidSocialTokenException("Apple ID Token에 subject가 없습니다")

        return SocialUserInfo(
            providerUserId = providerUserId,
            email = jwt.getClaimAsString("email"),
            provider = SocialProvider.APPLE,
        )
    }

    private fun buildDecoder(): NimbusJwtDecoder {
        val jwksJson = oidcPublicKeyCacheRepository.getJwks(PROVIDER_KEY) ?: fetchAndCacheJwks()

        val jwkSet = JWKSet.parse(jwksJson)
        val jwkSource = ImmutableJWKSet<SecurityContext>(jwkSet)

        return NimbusJwtDecoder.withJwkSource(jwkSource).build().also { decoder ->
            val audienceValidator = JwtClaimValidator<List<String>>(JwtClaimNames.AUD) { aud ->
                aud != null && aud.contains(oauth2Properties.apple.clientId)
            }

            val tokenValidator = DelegatingOAuth2TokenValidator(
                JwtTimestampValidator(),
                JwtIssuerValidator(APPLE_ISSUER),
                audienceValidator,
            )
            decoder.setJwtValidator(tokenValidator)
        }
    }

    private fun fetchAndCacheJwks(): String {
        val jwksJson = restClient.get()
            .retrieve()
            .body<String>()
            ?: throw InvalidSocialTokenException("Apple JWKS 응답이 비어있습니다")

        runCatching {
            JWKSet.parse(jwksJson)
        }.getOrElse {
            logger.warn { "Invalid Apple JWKS response" }
            throw InvalidSocialTokenException("Apple JWKS 응답이 유효하지 않습니다.")
        }

        oidcPublicKeyCacheRepository.saveJwks(PROVIDER_KEY, jwksJson)
        logger.info { "Apple JWKS fetched and cached" }
        return jwksJson
    }

    private fun isSignatureFailure(
        e: Throwable,
    ): Boolean {
        val message = e.message ?: ""
        return e.cause is JOSEException ||
            message.contains("Signed JWT rejected") ||
            message.contains("no matching key")
    }

    companion object {
        private const val APPLE_ISSUER = "https://appleid.apple.com"
        private const val PROVIDER_KEY = "apple"
    }
}
