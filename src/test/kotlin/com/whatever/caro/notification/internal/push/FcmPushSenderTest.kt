package com.whatever.caro.notification.internal.push

import com.google.firebase.messaging.BatchResponse
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingException
import com.google.firebase.messaging.MessagingErrorCode
import com.google.firebase.messaging.SendResponse
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class FcmPushSenderTest :
    DescribeSpec({
        val message = PushMessage(title = "title", body = "body")

        fun success(): SendResponse = mockk { every { isSuccessful } returns true }

        fun failure(
            code: MessagingErrorCode?,
        ): SendResponse {
            val exception = mockk<FirebaseMessagingException>(relaxed = true) {
                every { messagingErrorCode } returns code
            }
            return mockk {
                every { isSuccessful } returns false
                every { this@mockk.exception } returns exception
            }
        }

        fun batchOf(
            results: List<SendResponse>,
        ): BatchResponse =
            mockk {
                every { responses } returns results
                every { successCount } returns results.count { it.isSuccessful }
                every { failureCount } returns results.count { !it.isSuccessful }
            }

        describe("send") {
            it("UNREGISTERED, SENDER_ID_MISMATCH 토큰만 무효 토큰으로 반환한다") {
                val firebaseMessaging = mockk<FirebaseMessaging>()
                every { firebaseMessaging.sendEachForMulticast(any()) } returns batchOf(
                    listOf(
                        success(),
                        failure(MessagingErrorCode.UNREGISTERED),
                        failure(MessagingErrorCode.SENDER_ID_MISMATCH),
                        failure(MessagingErrorCode.UNAVAILABLE),
                    ),
                )
                val sender = FcmPushSender(firebaseMessaging)

                val result = sender.send(tokens = listOf("ok", "gone", "mismatch", "retryable"), message = message)

                result.successCount shouldBe 1
                result.failureCount shouldBe 3
                result.invalidTokens shouldContainExactly listOf("gone", "mismatch")
            }

            it("일시적 오류(UNAVAILABLE 등)는 무효 토큰으로 취급하지 않는다") {
                val firebaseMessaging = mockk<FirebaseMessaging>()
                every { firebaseMessaging.sendEachForMulticast(any()) } returns batchOf(
                    listOf(failure(MessagingErrorCode.INTERNAL), failure(null)),
                )
                val sender = FcmPushSender(firebaseMessaging)

                val result = sender.send(tokens = listOf("a", "b"), message = message)

                result.failureCount shouldBe 2
                result.invalidTokens.shouldBeEmpty()
            }

            it("토큰이 500개를 넘으면 500개 단위로 나눠 발송하고 결과를 합산한다") {
                val firebaseMessaging = mockk<FirebaseMessaging>()
                every { firebaseMessaging.sendEachForMulticast(any()) } returnsMany listOf(
                    batchOf(List(500) { success() }),
                    batchOf(listOf(failure(MessagingErrorCode.UNREGISTERED))),
                )
                val sender = FcmPushSender(firebaseMessaging)
                val tokens = List(501) { "token-$it" }

                val result = sender.send(tokens = tokens, message = message)

                verify(exactly = 2) { firebaseMessaging.sendEachForMulticast(any()) }
                result.successCount shouldBe 500
                result.failureCount shouldBe 1
                // 두 번째 청크의 0번 인덱스 = 전체 500번 토큰
                result.invalidTokens shouldContainExactly listOf("token-500")
            }
        }
    })
