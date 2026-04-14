package com.whatever.caro.auth.internal.token

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Redis 기반 Access Token Jti 저장소
 */
@Repository
class TokenBlacklistRepository(
    private val redisTemplate: StringRedisTemplate,
) {
    fun add(
        jti: String,
        expiresIn: Duration,
    ) {
        try {
            redisTemplate.opsForValue().set(key(jti), "", expiresIn)
        } catch (e: RedisConnectionFailureException) {
            logger.error { "Redis unavailable during blacklist add for jti=$jti -- token remains valid" }
            throw e
        }
    }

    fun isBlacklisted(
        jti: String,
    ): Boolean =
        try {
            redisTemplate.hasKey(key(jti)) == true
        } catch (e: RedisConnectionFailureException) {
            logger.error { "Redis unavailable during blacklist check for jti=$jti -- fail-closed, rejecting request" }
            true
        }

    private fun key(
        jti: String,
    ): String = "${KEY_PREFIX}:$jti"

    companion object {
        private const val KEY_PREFIX = "blacklist"
    }
}
