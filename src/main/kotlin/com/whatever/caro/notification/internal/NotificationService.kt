package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.notification.internal.push.PushResult
import com.whatever.caro.notification.internal.push.PushSender
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val logger = KotlinLogging.logger {}

@Service
class NotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val pushSender: PushSender,
    private val clock: Clock,
) {
    /**
     * 기기 토큰을 등록(upsert)한다. 앱 실행 시, FirebaseMessagingService.onNewToken 시점에 호출된다.
     */
    @Transactional
    fun registerToken(
        userId: Long,
        token: String,
        platform: DevicePlatform,
        timezone: ZoneId,
        locale: Locale,
    ) {
        val now = Instant.now(clock)
        val existing = deviceTokenRepository.findByToken(token = token)
        if (existing == null) {
            deviceTokenRepository.save(
                DeviceToken(
                    userId = userId,
                    token = token,
                    platform = platform,
                    timezone = timezone,
                    locale = locale,
                    lastSeenAt = now,
                ),
            )
            return
        }
        existing.refresh(
            userId = userId,
            platform = platform,
            timezone = timezone,
            locale = locale,
            now = now,
        )
    }

    /**
     * 로그아웃 시 해당 기기 토큰을 삭제한다. 본인 소유 토큰만 삭제되며, 없으면 무시한다(멱등).
     */
    fun unregisterToken(
        userId: Long,
        token: String,
    ) {
        deviceTokenRepository.deleteByUserIdAndToken(userId = userId, token = token)
    }

    /**
     * 유저의 모든 기기로 푸시를 보낸다.
     * 외부 I/O이므로 트랜잭션 밖에서 호출해야 하며, 만료/무효 토큰은 발송 후 정리한다.
     */
    fun sendToUser(
        userId: Long,
        message: PushMessage,
    ): PushResult {
        val tokens = deviceTokenRepository.findAllByUserId(userId = userId).map { it.token }
        if (tokens.isEmpty()) {
            logger.debug { "No device token. skip push. userId=$userId" }
            return PushResult.EMPTY
        }

        val result = pushSender.send(tokens = tokens, message = message)
        if (result.invalidTokens.isNotEmpty()) {
            val deleted = deviceTokenRepository.deleteAllByTokenIn(tokens = result.invalidTokens)
            logger.info { "Invalid device tokens removed. userId=$userId count=$deleted" }
        }
        return result
    }
}
