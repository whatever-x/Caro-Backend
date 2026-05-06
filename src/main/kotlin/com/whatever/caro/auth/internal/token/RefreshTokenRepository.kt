package com.whatever.caro.auth.internal.token

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import java.time.Duration

private val logger = KotlinLogging.logger {}

data class ConsumedToken(
    val accessTokenJti: String,
)

/**
 * Redis 기반 Refresh Token 저장소
 *
 * Forward key: refresh:{userId}:{deviceId} → {refreshToken}
 * Token pair key: token_pair:{refreshToken} → {accessTokenJti}
 */
@Repository
class RefreshTokenRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun save(
        userId: Long,
        deviceId: String,
        refreshToken: String,
        accessTokenJti: String,
        expiresIn: Duration,
    ) {
        try {
            val keys = listOf(
                key(userId, deviceId),
                tokenPairKey(refreshToken),
            )
            val args = listOf(
                refreshToken,
                accessTokenJti,
                expiresIn.seconds.toString(),
            )
            redisTemplate.execute(
                SAVE_SCRIPT,
                keys,
                *args.toTypedArray(),
            )
        } catch (e: RedisConnectionFailureException) {
            logger.error { "Redis unavailable during refresh token save: userId=$userId, deviceId=$deviceId" }
            throw e
        }
    }

    /**
     * Refresh Token을 원자적으로 조회하면서 삭제.
     * token_pair key를 GETDEL로 가져오고, forward key의 refreshToken과 비교하여 stale 검증.
     */
    fun consumeToken(
        refreshToken: String,
        userId: Long,
        deviceId: String,
    ): ConsumedToken? {
        try {
            val keys = listOf(tokenPairKey(refreshToken))
            val args = listOf(
                userId.toString(),
                deviceId,
                refreshToken,
            )
            val accessJti = redisTemplate.execute(
                CONSUME_SCRIPT,
                keys,
                *args.toTypedArray(),
            ) ?: return null

            return ConsumedToken(accessTokenJti = accessJti)
        } catch (e: RedisConnectionFailureException) {
            logger.error { "Redis unavailable during refresh token consume" }
            throw e
        }
    }

    /**
     * 로그아웃 시 해당 Device의 refresh token을 삭제
     *
     * forward key와 token pair를 바원자적으로 제거하므로,
     * 사이에 재로그인이 발생한다면 새로운 세션의 token pair가 제거될 수 있음
     */
    fun deleteByUserDevice(
        userId: Long,
        deviceId: String,
    ) {
        try {
            val forwardKey = key(userId, deviceId)
            val refreshToken = redisTemplate.opsForValue().getAndDelete(forwardKey) ?: return
            redisTemplate.delete(tokenPairKey(refreshToken))
        } catch (e: RedisConnectionFailureException) {
            logger.error { "Redis unavailable during refresh token delete: userId=$userId, deviceId=$deviceId" }
            throw e
        }
    }

    private fun key(
        userId: Long,
        deviceId: String,
    ): String = "$KEY_PREFIX:$userId:$deviceId"

    private fun tokenPairKey(
        token: String,
    ): String = "$TOKEN_PAIR_KEY_PREFIX:$token"

    companion object {
        private const val KEY_PREFIX = "refresh"
        private const val TOKEN_PAIR_KEY_PREFIX = "token_pair"

        private val SAVE_SCRIPT = RedisScript.of(
            """
                -- KEYS[1] = forward key (refresh:userId:deviceId)
                -- KEYS[2] = token_pair key (token_pair:refreshToken)
                -- ARGV[1] = refreshToken (forward value)
                -- ARGV[2] = accessTokenJti (token_pair value)
                -- ARGV[3] = expiry seconds

                local previous = redis.call('GETDEL', KEYS[1])
                if previous then
                    redis.call('DEL', '${TOKEN_PAIR_KEY_PREFIX}:' .. previous)
                end

                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
                redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
                return true
            """.trimIndent(),
            Boolean::class.java,
        )

        private val CONSUME_SCRIPT = RedisScript.of(
            """
                -- KEYS[1] = token_pair key (token_pair:refreshToken)
                -- ARGV[1] = userId
                -- ARGV[2] = deviceId
                -- ARGV[3] = refreshToken

                local accessJti = redis.call('GET', KEYS[1])
                if not accessJti then
                    return nil
                end

                local forwardKey = '${KEY_PREFIX}:' .. ARGV[1] .. ':' .. ARGV[2]
                local storedToken = redis.call('GET', forwardKey)

                if storedToken == ARGV[3] then
                    redis.call('DEL', KEYS[1])
                    redis.call('DEL', forwardKey)
                    return accessJti
                else
                    return nil
                end
            """.trimIndent(),
            String::class.java,
        )
    }
}
