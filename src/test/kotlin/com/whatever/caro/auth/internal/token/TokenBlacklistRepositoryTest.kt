package com.whatever.caro.auth.internal.token

import com.whatever.caro.CaroModuleTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

@CaroModuleTest(extraIncludes = ["common", "user"])
class TokenBlacklistRepositoryTest(
    private val tokenBlacklistRepository: TokenBlacklistRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

    afterEach {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    describe("add") {
        it("블랙리스트에 jti를 추가하면 조회 가능하다") {
            val jti = "test-jti-001"

            tokenBlacklistRepository.add(jti, Duration.ofMinutes(30))

            tokenBlacklistRepository.isBlacklisted(jti).shouldBeTrue()
        }

        it("TTL 만료 후 블랙리스트에서 제거된다") {
            val jti = "test-jti-expire"

            val ttl = Duration.ofMillis(100)
            tokenBlacklistRepository.add(jti, ttl)

            tokenBlacklistRepository.isBlacklisted(jti).shouldBeTrue()

            Thread.sleep(ttl)
            tokenBlacklistRepository.isBlacklisted(jti).shouldBeFalse()
        }

        it("여러 jti를 독립적으로 관리한다") {
            val jti1 = "jti-1"
            val jti2 = "jti-2"

            tokenBlacklistRepository.add(jti1, Duration.ofMinutes(10))
            tokenBlacklistRepository.add(jti2, Duration.ofMinutes(10))

            tokenBlacklistRepository.isBlacklisted(jti1).shouldBeTrue()
            tokenBlacklistRepository.isBlacklisted(jti2).shouldBeTrue()
        }
    }

    describe("isBlacklisted") {
        it("등록되지 않은 jti는 false를 반환한다") {
            tokenBlacklistRepository.isBlacklisted("unknown-jti").shouldBeFalse()
        }
    }
})
