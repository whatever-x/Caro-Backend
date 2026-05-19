package com.whatever.caro.common.web.idempotency

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Repository
import tools.jackson.databind.json.JsonMapper
import java.time.Duration

@Repository
class IdempotencyRepository(
    private val redisTemplate: StringRedisTemplate,
    private val jsonMapper: JsonMapper,
) {
    fun acquireIdempotencyProcessing(
        key: String,
        expiresIn: Duration,
    ): Boolean =
        redisTemplate.opsForValue().setIfAbsent(
            "$key:processing",
            "1",
            expiresIn,
        ) == true

    fun saveResponse(
        key: String,
        hash: String,
        statusCode: Int,
        contentType: String,
        body: String,
        expiresIn: Duration,
    ) {
        val payload = IdempotencyEntry(
            hash = hash,
            statusCode = statusCode,
            contentType = contentType,
            body = body,
        )

        val keys = listOf(
            key,
            "$key:processing",
        )
        val args = listOf(
            jsonMapper.writeValueAsString(payload),
            expiresIn.seconds.toString(),
        )
        redisTemplate.execute(
            SCRIPT,
            keys,
            *args.toTypedArray(),
        )
    }

    fun getCachedResponse(
        key: String,
    ): IdempotencyEntry? {
        val payload = redisTemplate.opsForValue().get(key) ?: return null
        return jsonMapper.readValue(payload, IdempotencyEntry::class.java)
    }

    fun deleteIdempotencyProcessing(
        key: String,
    ) {
        redisTemplate.delete("$key:processing")
    }

    companion object {
        private val SCRIPT = RedisScript.of(
            """
                -- KEYS[1] = redis key
                -- KEYS[2] = processing key
                -- ARGV[1] = idempotency entry json string
                -- ARGV[2] = expiry seconds

                redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
                redis.call('DEL', KEYS[2])
                return 1
            """.trimIndent(),
            Long::class.java,
        )
    }
}

data class IdempotencyEntry(
    val hash: String,
    val statusCode: Int,
    val contentType: String,
    val body: String,
)
