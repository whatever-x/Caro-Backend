package com.whatever.caro.auth.internal.token

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.comparables.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@ApplicationModuleTest(extraIncludes = ["common", "user"])
@Import(TestcontainersConfiguration::class)
class RefreshTokenRepositoryTest(
    private val refreshTokenRepository: RefreshTokenRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

    afterEach {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    describe("save") {
        it("forward key에 refreshToken, token_pair key에 accessJti가 저장된다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "token-abc"
            val accessTokenJti = "jti-123"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = accessTokenJti,
                expiresIn = Duration.ofMinutes(30),
            )

            redisTemplate.opsForValue().get("refresh:$userId:$deviceId") shouldBe refreshToken
            redisTemplate.opsForValue().get("token_pair:$refreshToken") shouldBe accessTokenJti
        }

        it("같은 userId:deviceId로 재저장 시 이전 token_pair key가 삭제된다") {
            val userId = 1L
            val deviceId = "device-1"
            val oldToken = "old-token"
            val newToken = "new-token"
            val newJti = "new-jti"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = oldToken,
                accessTokenJti = "old-jti",
                expiresIn = Duration.ofMinutes(30),
            )
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = newToken,
                accessTokenJti = newJti,
                expiresIn = Duration.ofMinutes(30),
            )

            redisTemplate.hasKey("token_pair:$oldToken") shouldBe false

            redisTemplate.opsForValue().get("refresh:$userId:$deviceId") shouldBe newToken
            redisTemplate.opsForValue().get("token_pair:$newToken") shouldBe newJti
        }

        it("다른 deviceId는 독립적으로 저장된다") {
            val userId = 1L
            val deviceId1 = "device-1"
            val deviceId2 = "device-2"
            val tokenA = "token-a"
            val tokenB = "token-b"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId1,
                refreshToken = tokenA,
                accessTokenJti = "jti-a",
                expiresIn = Duration.ofMinutes(30),
            )
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId2,
                refreshToken = tokenB,
                accessTokenJti = "jti-b",
                expiresIn = Duration.ofMinutes(30),
            )

            redisTemplate.opsForValue().get("refresh:$userId:$deviceId1") shouldBe tokenA
            redisTemplate.opsForValue().get("refresh:$userId:$deviceId2") shouldBe tokenB
        }

        it("TTL이 설정된다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "token-ttl"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = "jti-ttl",
                expiresIn = Duration.ofMinutes(30),
            )

            val forwardTtl = redisTemplate.getExpire("refresh:$userId:$deviceId").seconds
            val pairTtl = redisTemplate.getExpire("token_pair:$refreshToken").seconds

            forwardTtl.shouldBeBetween(1.seconds, 30.minutes)
            pairTtl.shouldBeBetween(1.minutes, 30.minutes)
        }
    }

    describe("consumeToken") {
        it("정상적으로 토큰을 소비하면 accessTokenJti를 반환한다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "consume-token"
            val accessTokenJti = "jti-consume"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = accessTokenJti,
                expiresIn = Duration.ofMinutes(30),
            )

            val result = refreshTokenRepository.consumeToken(refreshToken, userId, deviceId)

            result.shouldNotBeNull()
            result.accessTokenJti shouldBe accessTokenJti
        }

        it("소비 후 forward key와 token_pair key가 모두 삭제된다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "consume-token-2"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = "jti-2",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.consumeToken(refreshToken, userId, deviceId)

            redisTemplate.hasKey("token_pair:$refreshToken") shouldBe false
            redisTemplate.hasKey("refresh:$userId:$deviceId") shouldBe false
        }

        it("존재하지 않는 토큰은 null을 반환한다") {
            refreshTokenRepository.consumeToken(
                refreshToken = "nonexistent-token",
                userId = 1L,
                deviceId = "device-1",
            ).shouldBeNull()
        }

        it("이미 소비된 토큰 재사용 시 null을 반환한다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "once-token"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = "jti-once",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.consumeToken(refreshToken, userId, deviceId).shouldNotBeNull()
            refreshTokenRepository.consumeToken(refreshToken, userId, deviceId).shouldBeNull()
        }

        it("다른 디바이스로 consume 시도 시 null을 반환하고 정상 세션은 보존된다") {
            val refreshToken = "token-x"
            val accessTokenJti = "jti-x"

            val userId = 1L
            val deviceId = "device-1"
            val wrongDeviceId = "device-2"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = accessTokenJti,
                expiresIn = Duration.ofMinutes(30),
            )

            // wrongDeviceId로 시도
            // forward key 불일치로 실패하지만 token_pair는 보존
            val result = refreshTokenRepository.consumeToken(refreshToken, userId, wrongDeviceId)
            result.shouldBeNull()

            // 정상 세션 유지 확인
            redisTemplate.opsForValue().get("refresh:$userId:$deviceId") shouldBe refreshToken
            redisTemplate.opsForValue().get("token_pair:$refreshToken") shouldBe accessTokenJti
        }

        it("`재발급 이전의 refresh token` 사용 시 null을 반환하고 현재 세션을 보존한다") {
            val userId = 1L
            val deviceId = "device-1"
            val oldToken = "token-old"
            val newToken = "token-new"
            val newJti = "jti-new"

            // 첫 로그인
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = oldToken,
                accessTokenJti = "jti-old",
                expiresIn = Duration.ofMinutes(30),
            )

            // 같은 디바이스 재로그인 (oldToken의 token_pair은 SAVE_SCRIPT에서 삭제됨)
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = newToken,
                accessTokenJti = newJti,
                expiresIn = Duration.ofMinutes(30),
            )

            // oldToken으로 consume 시도
            refreshTokenRepository.consumeToken(oldToken, userId, deviceId).shouldBeNull()
            redisTemplate.hasKey("token_pair:$oldToken") shouldBe false // 재로그인 과정에서 token_pair는 이미 제거

            // 현재 세션 유지 확인
            redisTemplate.opsForValue().get("refresh:$userId:$deviceId") shouldBe newToken
            redisTemplate.opsForValue().get("token_pair:$newToken") shouldBe newJti
        }
    }

    describe("deleteByUserDevice") {
        it("해당 디바이스의 forward key와 token_pair key를 삭제한다") {
            val userId = 1L
            val deviceId = "device-1"
            val refreshToken = "token-del"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId,
                refreshToken = refreshToken,
                accessTokenJti = "jti-del",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.deleteByUserDevice(userId, deviceId)

            redisTemplate.hasKey("refresh:$userId:$deviceId") shouldBe false
            redisTemplate.hasKey("token_pair:$refreshToken") shouldBe false
        }

        it("다른 디바이스의 토큰에는 영향을 주지 않는다") {
            val userId = 1L
            val deviceId1 = "device-1"
            val deviceId2 = "device-2"
            val token2 = "token-d2"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId1,
                refreshToken = "token-d1",
                accessTokenJti = "jti-d1",
                expiresIn = Duration.ofMinutes(30),
            )
            refreshTokenRepository.save(
                userId = userId,
                deviceId = deviceId2,
                refreshToken = token2,
                accessTokenJti = "jti-d2",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.deleteByUserDevice(userId, deviceId1)

            redisTemplate.hasKey("refresh:$userId:$deviceId1") shouldBe false
            redisTemplate.opsForValue().get("refresh:$userId:$deviceId2") shouldBe token2
        }

        it("해당 디바이스의 토큰이 없어도 예외 없이 정상 종료한다") {
            refreshTokenRepository.deleteByUserDevice(
                userId = 999L,
                deviceId = "nonexistent-device",
            )
        }
    }

    describe("deleteAllByUser") {
        it("해당 유저의 모든 디바이스 forward key와 token_pair key를 삭제한다") {
            val userId = 1L
            val tokenA = "token-a"
            val tokenB = "token-b"
            refreshTokenRepository.save(
                userId = userId,
                deviceId = "device-1",
                refreshToken = tokenA,
                accessTokenJti = "jti-a",
                expiresIn = Duration.ofMinutes(30),
            )
            refreshTokenRepository.save(
                userId = userId,
                deviceId = "device-2",
                refreshToken = tokenB,
                accessTokenJti = "jti-b",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.deleteAllByUser(userId)

            redisTemplate.hasKey("refresh:$userId:device-1") shouldBe false
            redisTemplate.hasKey("refresh:$userId:device-2") shouldBe false
            redisTemplate.hasKey("token_pair:$tokenA") shouldBe false
            redisTemplate.hasKey("token_pair:$tokenB") shouldBe false
        }

        it("다른 유저의 토큰에는 영향을 주지 않는다") {
            val targetUserId = 1L
            val otherUserId = 2L
            val otherToken = "token-other"
            refreshTokenRepository.save(
                userId = targetUserId,
                deviceId = "device-1",
                refreshToken = "token-target",
                accessTokenJti = "jti-target",
                expiresIn = Duration.ofMinutes(30),
            )
            refreshTokenRepository.save(
                userId = otherUserId,
                deviceId = "device-1",
                refreshToken = otherToken,
                accessTokenJti = "jti-other",
                expiresIn = Duration.ofMinutes(30),
            )

            refreshTokenRepository.deleteAllByUser(targetUserId)

            redisTemplate.hasKey("refresh:$targetUserId:device-1") shouldBe false
            redisTemplate.opsForValue().get("refresh:$otherUserId:device-1") shouldBe otherToken
            redisTemplate.opsForValue().get("token_pair:$otherToken") shouldBe "jti-other"
        }

        it("삭제할 토큰이 없어도 예외 없이 정상 종료한다") {
            refreshTokenRepository.deleteAllByUser(999L)
        }
    }
})
