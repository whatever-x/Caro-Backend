package com.whatever.caro.notification.internal

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.notification.internal.push.PushMessage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

/**
 * notification 모듈 통합 테스트 (MySQL Testcontainers).
 * Flyway 마이그레이션 ↔ 엔티티 매핑과 unique 제약 기반 upsert 동작을 검증한다.
 * FIREBASE_CREDENTIALS_BASE64가 없으므로 PushSender는 LoggingPushSender가 주입된다.
 * 컨트롤러의 API 버전 매핑(WebMvcConfig)이 common에 있으므로 다른 모듈 테스트와 같이 common을 포함한다.
 */
@CaroModuleTest(extraIncludes = ["common"])
class NotificationServiceTest(
    private val notificationService: NotificationService,
    private val deviceTokenRepository: DeviceTokenRepository,
) : DescribeSpec({

    fun uniqueToken() = "token-${UUID.randomUUID()}"

    describe("registerToken") {
        it("같은 토큰을 다른 유저가 등록하면 행은 1개로 유지되고 소유자가 바뀐다") {
            val token = uniqueToken()

            notificationService.registerToken(userId = 1001L, token = token, platform = DevicePlatform.ANDROID)
            notificationService.registerToken(userId = 1002L, token = token, platform = DevicePlatform.ANDROID)

            val saved = deviceTokenRepository.findByToken(token).shouldNotBeNull()
            saved.userId shouldBe 1002L
            deviceTokenRepository.findAllByUserId(1001L).filter { it.token == token }.shouldBeEmpty()
        }
    }

    describe("unregisterToken") {
        it("다른 유저의 토큰은 삭제되지 않는다") {
            val token = uniqueToken()
            notificationService.registerToken(userId = 2001L, token = token, platform = DevicePlatform.ANDROID)

            notificationService.unregisterToken(userId = 9999L, token = token)

            deviceTokenRepository.findByToken(token).shouldNotBeNull()
        }

        it("본인 토큰은 삭제된다") {
            val token = uniqueToken()
            notificationService.registerToken(userId = 2002L, token = token, platform = DevicePlatform.ANDROID)

            notificationService.unregisterToken(userId = 2002L, token = token)

            deviceTokenRepository.findByToken(token) shouldBe null
        }
    }

    describe("sendToUser") {
        it("유저의 모든 기기로 발송한다") {
            val userId = 3001L
            notificationService.registerToken(userId, uniqueToken(), DevicePlatform.ANDROID)
            notificationService.registerToken(userId, uniqueToken(), DevicePlatform.IOS)

            val result = notificationService.sendToUser(
                userId = userId,
                message = PushMessage(title = "title", body = "body"),
            )

            result.successCount shouldBe 2
            result.invalidTokens.shouldBeEmpty()
        }
    }
})
