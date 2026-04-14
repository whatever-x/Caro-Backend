package com.whatever.caro.auth.internal.social

import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.redis.RedisConnectionFailureException
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration

private val logger = KotlinLogging.logger {}

@Repository
class OidcPublicKeyCacheRepository(
    private val redisTemplate: StringRedisTemplate,
) {

    fun getJwks(
        provider: String,
    ): String? =
        try {
            redisTemplate.opsForValue().get(key(provider))
        } catch (e: RedisConnectionFailureException) {
            logger.warn { "Redis unavailable during OIDC JWKS cache lookup: provider=$provider" }
            null
        }

    fun saveJwks(
        provider: String,
        jwksJson: String,
        ttl: Duration = Duration.ofHours(24),
    ) {
        try {
            redisTemplate.opsForValue().set(key(provider), jwksJson, ttl)
        } catch (e: RedisConnectionFailureException) {
            logger.warn { "Redis unavailable during OIDC JWKS cache save: provider=$provider" }
        }
    }

    fun evict(
        provider: String,
    ) {
        try {
            redisTemplate.delete(key(provider))
            logger.info { "OIDC JWKS cache evicted: provider=$provider" }
        } catch (e: RedisConnectionFailureException) {
            logger.warn { "Redis unavailable during OIDC JWKS cache eviction: provider=$provider" }
        }
    }

    private fun key(
        provider: String,
    ): String = "$KEY:$provider"

    companion object {
        private const val KEY = "oidc:jwks"
    }
}
