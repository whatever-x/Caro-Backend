package com.whatever.caro.common.web.idempotency

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Duration
import java.util.concurrent.TimeUnit

@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
class IdempotencyRepositoryTest(
    private val repository: IdempotencyRepository,
    private val redisTemplate: StringRedisTemplate,
) : DescribeSpec({

    val processingTtl: Duration = Duration.ofSeconds(30)
    val responseTtl: Duration = Duration.ofHours(24)

    afterEach {
        redisTemplate.connectionFactory?.connection?.serverCommands()?.flushDb()
    }

    describe("acquireIdempotencyProcessing()") {
        it("신규 키일 때 true를 리턴하고 processing 마커를 저장한다") {
            val key = "idempotency:test-key-1"

            val result = repository.acquireIdempotencyProcessing(key, processingTtl)

            result shouldBe true
            redisTemplate.hasKey("$key:processing") shouldBe true
        }

        it("이미 존재하는 키라면 false를 리턴한다") {
            val key = "idempotency:test-key-2"
            repository.acquireIdempotencyProcessing(key, processingTtl)

            val result = repository.acquireIdempotencyProcessing(key, processingTtl)

            result shouldBe false
        }
    }

    describe("saveResponse()") {
        it("response 키에 payload를 저장하고 processing 키를 삭제한다") {
            val key = "idempotency:test-key-3"
            repository.acquireIdempotencyProcessing(key, processingTtl)
            redisTemplate.hasKey("$key:processing") shouldBe true

            repository.saveResponse(
                key = key,
                hash = "abc123hash",
                statusCode = 200,
                contentType = "application/json",
                body = """{"data":"hello"}""",
                expiresIn = responseTtl,
            )

            redisTemplate.hasKey(key) shouldBe true
            redisTemplate.hasKey("$key:processing") shouldBe false
        }
    }

    describe("getCachedResponse()") {
        it("저장된 response를 동일하게 조회한다") {
            val key = "idempotency:test-key-4"
            val hash = "abc123hash"
            val status = 200
            val contentType = "application/json"
            val body = """{"data":"hello"}"""
            repository.saveResponse(
                key = key,
                hash = hash,
                statusCode = status,
                contentType = contentType,
                body = body,
                expiresIn = responseTtl,
            )

            val cached = repository.getCachedResponse(key)

            cached.shouldNotBeNull()
            cached.hash shouldBe hash
            cached.statusCode shouldBe status
            cached.contentType shouldBe contentType
            cached.body shouldBe body
        }

        it("존재하지 않는 키는 null을 리턴한다") {
            val unknownKey = "idempotency:unknown-key"

            val result = repository.getCachedResponse(unknownKey)

            result shouldBe null
        }
    }

    describe("deleteIdempotencyProcessing()") {
        it("processing 마커를 삭제한다") {
            val key = "idempotency:test-key-5"
            repository.acquireIdempotencyProcessing(key, processingTtl)
            redisTemplate.hasKey("$key:processing") shouldBe true

            repository.deleteIdempotencyProcessing(key)

            redisTemplate.hasKey("$key:processing") shouldBe false
        }
    }

    describe("TTL test") {
        it("saveResponse시 전달한 ttl이 설정된다") {
            val key = "idempotency:test-key-6"

            repository.saveResponse(
                key = key,
                hash = "hashval",
                statusCode = 200,
                contentType = "application/json",
                body = "{}",
                expiresIn = responseTtl,
            )
            val ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS)

            ttlSeconds shouldBeIn (responseTtl.seconds - 60..responseTtl.seconds)
        }

        it("ttl 만료 후 response 키가 자동 삭제된다") {
            val key = "idempotency:test-key-7"
            val shortTtl = Duration.ofSeconds(1)

            repository.saveResponse(
                key = key,
                hash = "hashval",
                statusCode = 200,
                contentType = "application/json",
                body = "{}",
                expiresIn = shortTtl,
            )
            redisTemplate.hasKey(key) shouldBe true

            await()
                .pollDelay(shortTtl.minusMillis(100))
                .between(
                    shortTtl.minusMillis(100),
                    shortTtl.plusMillis(100),
                )
                .untilAsserted {
                    redisTemplate.hasKey(key) shouldBe false
                }
        }
    }
})
