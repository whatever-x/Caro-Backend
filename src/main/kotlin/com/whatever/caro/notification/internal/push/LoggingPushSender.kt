package com.whatever.caro.notification.internal.push

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * FCM 자격증명이 없을 때(로컬/테스트) 실제 발송 대신 로그만 남긴다.
 */
class LoggingPushSender : PushSender {
    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushResult {
        logger.info { "[LoggingPushSender] push skipped. tokens=${tokens.size} title=${message.title}" }
        return PushResult(successCount = tokens.size, failureCount = 0, invalidTokens = emptyList())
    }
}
