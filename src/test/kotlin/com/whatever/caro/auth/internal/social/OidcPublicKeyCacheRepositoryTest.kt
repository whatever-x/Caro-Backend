package com.whatever.caro.auth.internal.social

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ApplicationModuleTest(extraIncludes = ["common", "user"])
@Import(TestcontainersConfiguration::class)
class OidcPublicKeyCacheRepositoryTest(
    private val oidcPublicKeyCacheRepository: OidcPublicKeyCacheRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

    afterEach {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    describe("getJwks") {
        it("저장된 JWKS를 반환한다") {
            val provider = "apple"
            val jwksJson = """{"keys":[{"kty":"RSA","kid":"test-key-id"}]}"""
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = jwksJson,
            )

            val result = oidcPublicKeyCacheRepository.getJwks(provider = provider)

            result shouldBe jwksJson
        }

        it("존재하지 않는 provider는 null을 반환한다") {
            val result = oidcPublicKeyCacheRepository.getJwks(provider = "nonexistent-provider")
            result.shouldBeNull()
        }
    }

    describe("saveJwks") {
        it("JWKS를 저장하고 기본 TTL(24시간)이 설정된다") {
            val provider = "apple"
            val jwksJson = """{"keys":[{"kty":"RSA","kid":"test-key-id"}]}"""
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = jwksJson,
            )

            val ttl = redisTemplate.getExpire("oidc:jwks:$provider").seconds

            ttl.shouldBeBetween(1.seconds, 24.hours)
        }

        it("커스텀 TTL이 적용된다") {
            val provider = "kakao"
            val jwksJson = """{"keys":[{"kty":"RSA","kid":"kakao-key-id"}]}"""
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = jwksJson,
                ttl = Duration.ofMinutes(30),
            )

            val ttl = redisTemplate.getExpire("oidc:jwks:$provider").seconds

            ttl.shouldBeBetween(1.seconds, 30.minutes)
        }

        it("같은 provider로 재저장 시 값이 덮어씌워진다") {
            val provider = "apple"
            val firstJwksJson = """{"keys":[{"kty":"RSA","kid":"first-key-id"}]}"""
            val secondJwksJson = """{"keys":[{"kty":"RSA","kid":"second-key-id"}]}"""
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = firstJwksJson,
            )
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = secondJwksJson,
            )

            val result = oidcPublicKeyCacheRepository.getJwks(provider = provider)

            result shouldBe secondJwksJson
        }
    }

    describe("evict") {
        it("저장된 JWKS를 삭제한다") {
            val provider = "apple"
            val jwksJson = """{"keys":[{"kty":"RSA","kid":"test-key-id"}]}"""
            oidcPublicKeyCacheRepository.saveJwks(
                provider = provider,
                jwksJson = jwksJson,
            )

            oidcPublicKeyCacheRepository.evict(provider = provider)

            val result = oidcPublicKeyCacheRepository.getJwks(provider = provider)
            result.shouldBeNull()
        }

        it("존재하지 않는 key evict 시 예외 없이 정상 종료한다") {
            oidcPublicKeyCacheRepository.evict(provider = "nonexistent-provider")
        }
    }
})
