package com.whatever.caro.notification.internal.web

import com.whatever.caro.notification.internal.DevicePlatform
import com.whatever.caro.notification.internal.NotificationService
import com.whatever.caro.notification.internal.web.request.RegisterDeviceTokenRequest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearAllMocks
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.http.HttpStatus
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean
import java.time.ZoneId
import java.util.Locale

class DeviceTokenControllerUnitTest :
    DescribeSpec({
        val notificationService = mockk<NotificationService>(relaxed = true)
        val controller = DeviceTokenController(notificationService = notificationService)

        beforeEach { clearAllMocks() }

        describe("기기 토큰 검증 메시지") {
            it("필수 및 길이 오류를 요청 언어로 표시하고 길이 상한을 치환한다") {
                val source = ResourceBundleMessageSource().apply {
                    setBasename("messages")
                    setDefaultEncoding("UTF-8")
                    setFallbackToSystemLocale(false)
                }
                val validator = LocalValidatorFactoryBean().apply {
                    setValidationMessageSource(source)
                    afterPropertiesSet()
                }
                try {
                    for (token in listOf("", "a".repeat(513))) {
                        val messages = listOf(Locale.ENGLISH, Locale.KOREAN).map { locale ->
                            LocaleContextHolder.setLocale(locale)
                            val violations = validator.validate(RegisterDeviceTokenRequest(token, DevicePlatform.ANDROID))
                            violations.size shouldBe 1
                            violations.single().message.also {
                                it.contains("{") shouldBe false
                                if (token.isNotEmpty()) it.contains("512") shouldBe true
                            }
                        }
                        messages[0] shouldNotBe messages[1]
                        Regex("[가-힣]").containsMatchIn(messages[0]) shouldBe false
                    }
                } finally {
                    LocaleContextHolder.resetLocaleContext()
                    validator.close()
                }
            }
        }

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
