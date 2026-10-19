package com.whatever.caro.notification.internal.push

/**
 * 푸시 발송 추상화. 테스트/로컬에서는 [LoggingPushSender]로 대체된다.
 */
interface PushSender {
    fun send(
        tokens: List<String>,
        message: PushMessage,
    ): PushResult
}

data class PushMessage(
    val title: String,
    val body: String,
    /** 앱에서 딥링크 등에 쓸 부가 데이터. FCM data payload는 String 값만 허용한다. */
    val data: Map<String, String> = emptyMap(),
)

data class PushResult(
    val successCount: Int,
    val failureCount: Int,
    /** 더 이상 유효하지 않아 삭제해야 하는 토큰 */
    val invalidTokens: List<String>,
) {
    companion object {
        val EMPTY = PushResult(successCount = 0, failureCount = 0, invalidTokens = emptyList())
    }
}
