package com.whatever.caro.notification.internal.web.response

import com.whatever.caro.notification.internal.push.PushResult
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "TestPushResponse", description = "테스트 푸시 발송 결과")
data class TestPushResponse(
    @field:Schema(description = "발송 성공 기기 수")
    val successCount: Int,
    @field:Schema(description = "발송 실패 기기 수")
    val failureCount: Int,
    @field:Schema(description = "무효 토큰으로 판단되어 삭제된 수")
    val removedTokenCount: Int,
)

fun PushResult.toTestPushResponse(): TestPushResponse =
    TestPushResponse(
        successCount = successCount,
        failureCount = failureCount,
        removedTokenCount = invalidTokens.size,
    )
