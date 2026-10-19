package com.whatever.caro.notification.internal.web

import com.whatever.caro.notification.internal.DevicePlatform
import com.whatever.caro.notification.internal.NotificationService
import com.whatever.caro.notification.internal.web.request.RegisterDeviceTokenRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus
import java.time.ZoneId
import java.util.Locale

class DeviceTokenControllerUnitTest :
    DescribeSpec({
        val notificationService = mockk<NotificationService>(relaxed = true)
        val controller = DeviceTokenController(notificationService = notificationService)

        beforeEach { clearAllMocks() }

        describe("registerToken") {
            it("토큰을 등록하고 204를 반환한다") {
                val request = RegisterDeviceTokenRequest(token = "fcm-token", platform = DevicePlatform.ANDROID)

                val response = controller.registerToken(
                    request = request,
                    timezone = ZoneId.of("Asia/Seoul"),
                    locale = Locale.KOREAN,
                    userId = 1L,
                )

                response.statusCode shouldBe HttpStatus.NO_CONTENT
                verify(exactly = 1) {
                    notificationService.registerToken(
                        userId = 1L,
                        token = "fcm-token",
                        platform = DevicePlatform.ANDROID,
                        timezone = ZoneId.of("Asia/Seoul"),
                        locale = Locale.KOREAN,
                    )
                }
            }
        }

        describe("unregisterToken") {
            it("토큰을 삭제하고 204를 반환한다") {
                val response = controller.unregisterToken(token = "fcm-token", userId = 1L)

                response.statusCode shouldBe HttpStatus.NO_CONTENT
                verify(exactly = 1) { notificationService.unregisterToken(userId = 1L, token = "fcm-token") }
            }
        }
    })
