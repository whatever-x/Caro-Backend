package com.whatever.caro.auth.internal.social

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.whatever.caro.CaroModuleTest
import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.auth.internal.config.OAuth2Properties
import com.whatever.caro.user.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.Date

private const val DEFAULT_PROVIDER_USER_ID = "000851.test-user-id.0747"

private const val APPLE_PROVIDER = "apple"

@CaroModuleTest(extraIncludes = ["common", "user"])
class AppleIdTokenVerifierTest(
    private val oidcPublicKeyCacheRepository: OidcPublicKeyCacheRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    val publicKey = keyPair.public as RSAPublicKey
    val privateKey = keyPair.private as RSAPrivateKey
    val kid = "test-apple-kid"

    val rsaKey = RSAKey.Builder(publicKey)
        .keyID(kid)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.RS256)
        .build()
    val jwksJson = JWKSet(rsaKey).toString()

    val clientIds = setOf("com.test.caro.1", "com.test.caro.2")
    val appleIssuer = "https://appleid.apple.com"
    val jwksUri = "https://appleid.apple.com/auth/keys"

    afterEach {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    fun createVerifier(): Pair<AppleIdTokenVerifier, MockRestServiceServer> {
        val builder = RestClient.builder()
        val jwksServer = MockRestServiceServer.bindTo(builder).build()
        val properties = OAuth2Properties(
            google = OAuth2Properties.GoogleProperties(clientId = "unused"),
            apple = OAuth2Properties.AppleProperties(
                clientIds = clientIds,
                jwksUri = jwksUri,
            ),
        )
        val verifier = AppleIdTokenVerifier(
            oauth2Properties = properties,
            oidcPublicKeyCacheRepository = oidcPublicKeyCacheRepository,
            restClientBuilder = builder,
        )
        return verifier to jwksServer
    }

    fun buildIdToken(
        sub: String? = DEFAULT_PROVIDER_USER_ID,
        iss: String = appleIssuer,
        aud: String = clientIds.last(),
        exp: Date = Date.from(Instant.now().plus(Duration.ofMinutes(10))),
        iat: Date = Date.from(Instant.now()),
        email: String? = "user@example.com",
        keyId: String = kid,
        signingKey: RSAPrivateKey = privateKey,
    ): String {
        val header = JWSHeader.Builder(JWSAlgorithm.RS256).keyID(keyId).build()
        val claimsBuilder = JWTClaimsSet.Builder()
            .issuer(iss)
            .audience(aud)
            .expirationTime(exp)
            .issueTime(iat)
        if (sub != null) {
            claimsBuilder.subject(sub)
        }
        if (email != null) {
            claimsBuilder.claim("email", email)
        }
        val signedJWT = SignedJWT(header, claimsBuilder.build())
        signedJWT.sign(RSASSASigner(signingKey))
        return signedJWT.serialize()
    }

    fun MockRestServiceServer.stubJwksResponse(
        jwks: String = jwksJson,
    ) {
        expect(requestTo(jwksUri))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(jwks, MediaType.APPLICATION_JSON))
    }

    describe("verify") {
        it("유효한 Apple ID Token에서 SocialUserInfo를 반환한다") {
            val providerUserId = DEFAULT_PROVIDER_USER_ID
            val email = "user@example.com"
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()
            val idToken = buildIdToken(sub = providerUserId, email = email)

            val result = verifier.verify(idToken = idToken)

            result.providerUserId shouldBe providerUserId
            result.email shouldBe email
            result.provider shouldBe SocialProvider.APPLE
        }

        it("provider가 APPLE이다") {
            val (verifier, _) = createVerifier()

            verifier.provider shouldBe SocialProvider.APPLE
        }

        it("만료된 토큰 시 InvalidSocialTokenException을 던진다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()

            val now = Instant.now()
            val issuedAt = now.minus(Duration.ofMinutes(11))
            val expiredAt = issuedAt.minusMillis(1) // 발급 시각 1ms 전 만료

            val expiredToken = buildIdToken(
                iat = Date.from(issuedAt),
                exp = Date.from(expiredAt),
            )

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = expiredToken)
            }
        }

        it("잘못된 audience 시 InvalidSocialTokenException을 던진다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()

            val invalidAudience = "com.wrong.app"
            val wrongAudToken = buildIdToken(aud = invalidAudience)

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = wrongAudToken)
            }
        }

        it("잘못된 issuer 시 InvalidSocialTokenException을 던진다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()

            val invalidIssuer = "https://evil.test.com"
            val wrongIssToken = buildIdToken(iss = invalidIssuer)

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = wrongIssToken)
            }
        }

        it("subject가 없는 토큰일 경우 InvalidSocialTokenException을 던진다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()
            val noSubToken = buildIdToken(sub = null)

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = noSubToken)
            }
        }

        it("email이 없는 경우에도 정상 검증된다") {
            val providerUserId = "000851.no-email-user.0747"
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()
            val noEmailToken = buildIdToken(sub = providerUserId, email = null)

            val result = verifier.verify(idToken = noEmailToken)

            result.providerUserId shouldBe providerUserId
            result.email shouldBe null
            result.provider shouldBe SocialProvider.APPLE
        }

        it("잘못된 서명의 토큰일 경우, InvalidSocialTokenException을 던진다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, jwksServer) = createVerifier()
            jwksServer.stubJwksResponse()

            val wrongKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val wrongPrivateKey = wrongKeyPair.private as RSAPrivateKey

            val wrongSignToken = buildIdToken(signingKey = wrongPrivateKey) // 잘못된 key로 서명된 토큰

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = wrongSignToken)
            }
            jwksServer.verify()
        }
    }

    describe("JWKS 캐시 테스트") {
        it("캐시된 JWKS로 검증 성공한다") {
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, _) = createVerifier()
            val idToken = buildIdToken()

            val result = verifier.verify(idToken = idToken)

            result.shouldNotBeNull()
            result.provider shouldBe SocialProvider.APPLE
        }

        it("캐시 미스 시 Apple JWKS 엔드포인트에서 가져와 저장한다") {
            val (verifier, jwksServer) = createVerifier()
            jwksServer.stubJwksResponse()

            val idToken = buildIdToken()
            val result = verifier.verify(idToken = idToken)

            result.providerUserId shouldBe DEFAULT_PROVIDER_USER_ID
            oidcPublicKeyCacheRepository.getJwks(provider = APPLE_PROVIDER).shouldNotBeNull()
            jwksServer.verify()
        }

        it("kid 불일치 시 캐시를 evict하고 새 키로 재시도한다") {
            val newKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val newPublicKey = newKeyPair.public as RSAPublicKey
            val newPrivateKey = newKeyPair.private as RSAPrivateKey
            val newKid = "rotated-apple-kid"

            val newRsaKey = RSAKey.Builder(newPublicKey)
                .keyID(newKid)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build()
            val newJwksJson = JWKSet(newRsaKey).toString()

            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson) // 캐시에 기존 키 저장
            val (verifier, jwksServer) = createVerifier()

            jwksServer.stubJwksResponse(jwks = newJwksJson) // 새 JWKS 반환하도록 stub
            val idToken = buildIdToken(keyId = newKid, signingKey = newPrivateKey) // 새 키로 서명된 토큰

            val result = verifier.verify(idToken = idToken)

            result.providerUserId shouldBe DEFAULT_PROVIDER_USER_ID
            result.provider shouldBe SocialProvider.APPLE
            jwksServer.verify()
        }

        it("재시도 후에도 실패하면 InvalidSocialTokenException을 던진다") {
            val unknownKeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val unknownPrivateKey = unknownKeyPair.private as RSAPrivateKey
            val unknownKid = "unknown-kid"

            // 캐시에 기존 키, MockServer도 기존 키 반환 (unknown kid는 여전히 매칭 안 됨)
            oidcPublicKeyCacheRepository.saveJwks(provider = APPLE_PROVIDER, jwksJson = jwksJson)
            val (verifier, jwksServer) = createVerifier()
            jwksServer.stubJwksResponse()

            val idToken = buildIdToken(keyId = unknownKid, signingKey = unknownPrivateKey)

            shouldThrow<InvalidSocialTokenException> {
                verifier.verify(idToken = idToken)
            }
        }
    }
})
