package com.whatever.caro.notification.internal

import com.whatever.caro.notification.internal.push.PushMessage
import com.whatever.caro.notification.internal.push.PushResult
import com.whatever.caro.notification.internal.push.PushSender
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class NotificationServiceUnitTest :
    DescribeSpec({
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        val deviceTokenRepository = mockk<DeviceTokenRepository>(relaxed = true)
        val pushSender = mockk<PushSender>()
        val service = NotificationService(
            deviceTokenRepository = deviceTokenRepository,
            pushSender = pushSender,
            clock = clock,
        )
        val message = PushMessage(title = "title", body = "body")

        fun deviceToken(
            userId: Long,
            token: String,
            platform: DevicePlatform = DevicePlatform.ANDROID,
            lastSeenAt: Instant = now.minusSeconds(3600),
        ) = DeviceToken(userId = userId, token = token, platform = platform, lastSeenAt = lastSeenAt)

        beforeEach { clearAllMocks() }

        describe("registerToken") {
            it("처음 보는 토큰이면 새로 저장한다") {
                every { deviceTokenRepository.findByToken("new-token") } returns null

                service.registerToken(userId = 1L, token = "new-token", platform = DevicePlatform.ANDROID)

                verify(exactly = 1) {
                    deviceTokenRepository.save(
                        match<DeviceToken> {
                            it.userId == 1L && it.token == "new-token" && it.lastSeenAt == now
                        },
                    )
                }
            }

            it("이미 있는 토큰이면 소유자/플랫폼/lastSeenAt을 갱신하고 새로 저장하지 않는다") {
                val existing = deviceToken(userId = 1L, token = "shared-device")
                every { deviceTokenRepository.findByToken("shared-device") } returns existing

                // 같은 기기에서 다른 계정(2)으로 로그인
                service.registerToken(userId = 2L, token = "shared-device", platform = DevicePlatform.IOS)

                existing.userId shouldBe 2L
                existing.platform shouldBe DevicePlatform.IOS
                existing.lastSeenAt shouldBe now
                verify(exactly = 0) { deviceTokenRepository.save(any<DeviceToken>()) }
            }
        }

        describe("sendToUser") {
            it("등록된 토큰이 없으면 발송하지 않는다") {
                every { deviceTokenRepository.findAllByUserId(1L) } returns emptyList()

                val result = service.sendToUser(userId = 1L, message = message)

                result shouldBe PushResult.EMPTY
                verify(exactly = 0) { pushSender.send(any(), any()) }
            }

            it("무효 토큰이 응답되면 해당 토큰만 삭제한다") {
                every { deviceTokenRepository.findAllByUserId(1L) } returns listOf(
                    deviceToken(userId = 1L, token = "alive"),
                    deviceToken(userId = 1L, token = "dead"),
                )
                every { pushSender.send(listOf("alive", "dead"), message) } returns PushResult(
                    successCount = 1,
                    failureCount = 1,
                    invalidTokens = listOf("dead"),
                )

                val result = service.sendToUser(userId = 1L, message = message)

                result.successCount shouldBe 1
                verify(exactly = 1) { deviceTokenRepository.deleteAllByTokenIn(listOf("dead")) }
            }

            it("무효 토큰이 없으면 삭제 쿼리를 실행하지 않는다") {
                every { deviceTokenRepository.findAllByUserId(1L) } returns listOf(deviceToken(1L, "alive"))
                every { pushSender.send(any(), any()) } returns PushResult(
                    successCount = 1,
                    failureCount = 0,
                    invalidTokens = emptyList(),
                )

                service.sendToUser(userId = 1L, message = message)

                verify(exactly = 0) { deviceTokenRepository.deleteAllByTokenIn(any()) }
            }
        }
    })
