package com.whatever.caro.notification.internal.push

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LoggingPushSenderTest :
    DescribeSpec({
        describe("send") {
            it("실제 발송 없이 모든 토큰을 성공으로 처리한다") {
                val sender = LoggingPushSender()

                val result = sender.send(
                    tokens = listOf("a", "b", "c"),
                    message = PushMessage(title = "title", body = "body"),
                )

                result.successCount shouldBe 3
                result.failureCount shouldBe 0
                result.invalidTokens.shouldBeEmpty()
            }
        }
    })
