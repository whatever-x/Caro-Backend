package com.whatever.caro.notification.internal.push

import com.google.firebase.messaging.AndroidConfig
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.MulticastMessage
import com.google.firebase.messaging.Notification
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class FcmPushSender(
    private val firebaseMessaging: FirebaseMessaging,
) : PushSender {

    override fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushResult {
        var successCount = 0
        var failureCount = 0
        val invalidTokens = mutableListOf<String>()

        tokens.chunked(MAX_TOKENS_PER_REQUEST).forEach { chunk ->
            val response = firebaseMessaging.sendEachForMulticast(buildMessage(chunk, message))
            successCount += response.successCount
            failureCount += response.failureCount

            response.responses.forEachIndexed { index, sendResponse ->
                if (sendResponse.isSuccessful) return@forEachIndexed
                val exception = sendResponse.exception
                val errorCode = exception?.messagingErrorCode
                if (errorCode in INVALID_TOKEN_ERRORS) {
                    invalidTokens += chunk[index]
                } else {
                    logger.warn { "FCM send failed. errorCode=$errorCode message=${exception?.message}" }
                }
            }
        }

        return PushResult(
            successCount = successCount,
            failureCount = failureCount,
            invalidTokens = invalidTokens,
        )
    }

    private fun buildMessage(
        tokens: List<String>,
        message: PushMessage,
    ): MulticastMessage =
        MulticastMessage.builder()
            .addAllTokens(tokens)
            .setNotification(
                Notification.builder()
                    .setTitle(message.title)
                    .setBody(message.body)
                    .build(),
            )
            .putAllData(message.data)
            .setAndroidConfig(
                AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build(),
            )
            .build()

    companion object {
        /** FCM multicast 1회 최대 토큰 수 */
        private const val MAX_TOKENS_PER_REQUEST = 500

        /** 앱 삭제/토큰 만료 등으로 재시도해도 성공할 수 없는 에러 */
        private val INVALID_TOKEN_ERRORS = setOf(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH,
        )
    }
}
