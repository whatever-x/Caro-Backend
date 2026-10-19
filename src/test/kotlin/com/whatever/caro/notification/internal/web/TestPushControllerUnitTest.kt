package com.whatever.caro.notification.internal.web

import com.whatever.caro.notification.internal.NotificationService
import com.whatever.caro.notification.internal.push.PushResult
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus

class TestPushControllerUnitTest :
    DescribeSpec({
        describe("sendTestPush") {
            it("로그인 유저에게 테스트 푸시를 보내고 발송 결과를 반환한다") {
                val notificationService = mockk<NotificationService>()
                every { notificationService.sendToUser(userId = 1L, message = any()) } returns PushResult(
                    successCount = 2,
                    failureCount = 1,
                    invalidTokens = listOf("dead"),
                )
                val controller = TestPushController(notificationService = notificationService)

                val response = controller.sendTestPush(userId = 1L)

                response.statusCode shouldBe HttpStatus.OK
                val data = response.body.shouldNotBeNull().data.shouldNotBeNull()
                data.successCount shouldBe 2
                data.failureCount shouldBe 1
                data.removedTokenCount shouldBe 1
                verify(exactly = 1) {
                    notificationService.sendToUser(
                        userId = 1L,
                        message = match { it.data["type"] == "TEST" },
                    )
                }
            }
        }
    })
