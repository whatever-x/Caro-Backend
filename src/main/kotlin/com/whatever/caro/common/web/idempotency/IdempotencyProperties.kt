package com.whatever.caro.common.web.idempotency

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

private val logger = KotlinLogging.logger {}

@ConfigurationProperties(prefix = "app.idempotency")
data class IdempotencyProperties(
    val responseTtl: Duration,
    val processingTtl: Duration,
) {
    @PostConstruct
    fun validate() {
        require(!responseTtl.isNegative && !responseTtl.isZero) {
            "app.idempotency.response-ttl must be positive: $responseTtl"
        }
        require(!processingTtl.isNegative && !processingTtl.isZero) {
            "app.idempotency.processing-ttl must be positive: $processingTtl"
        }
        logger.info {
            "IdempotencyProperties loaded: responseTtl=$responseTtl, processingTtl=$processingTtl"
        }
    }
}
