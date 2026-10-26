package com.whatever.caro.banner.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.banner.internal.BannerPlatform
import com.whatever.caro.banner.internal.HomeBannerService
import com.whatever.caro.banner.internal.web.response.HomeBannersResponse
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
import org.mockito.BDDMockito.given
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import java.time.Instant

@WebMvcTest(
    controllers = [HomeBannerController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class HomeBannerControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var service: HomeBannerService

    @MockitoBean
    lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean
    lateinit var jwtExceptionFilter: JwtExceptionFilter

    @MockitoBean
    lateinit var requestResponseLoggingFilter: RequestResponseLoggingFilter

    @MockitoBean
    lateinit var idempotencyRepository: IdempotencyRepository

    @MockitoBean
    lateinit var idempotencyProperties: IdempotencyProperties

    init {
        it("빈 목록도 버전 1.0의 성공 응답과 no-store를 반환한다") {
            given(service.list(BannerPlatform.ANDROID, "1.0.0"))
                .willReturn(HomeBannersResponse(emptyList(), Instant.parse("2026-10-24T00:00:00Z"), "empty"))
            mockMvc.get("/home/banners?platform=ANDROID&appVersion=1.0.0") {
                header("API-Version", "1.0")
            }.andExpect {
                status { isOk() }
                header { string("Cache-Control", "no-store") }
                jsonPath("$.data.items") { isEmpty() }
                jsonPath("$.data.configRevision") { value("empty") }
            }
        }
        it("누락된 앱 정보와 알 수 없는 플랫폼은 400이다") {
            listOf("/home/banners", "/home/banners?platform=ANDROID", "/home/banners?platform=WEB&appVersion=1.0.0").forEach { path ->
                mockMvc.get(path) { header("API-Version", "1.0") }.andExpect { status { isBadRequest() } }
            }
        }
        it("잘못된 앱 버전은 400 C002로 응답한다") {
            given(service.list(BannerPlatform.ANDROID, "bad"))
                .willThrow(BusinessException(CommonErrorCode.INVALID_INPUT))
            mockMvc.get("/home/banners?platform=ANDROID&appVersion=bad") {
                header("API-Version", "1.0")
            }.andExpect {
                status { isBadRequest() }
                jsonPath("$.error.code") { value(CommonErrorCode.INVALID_INPUT.code) }
            }
        }
    }
}
