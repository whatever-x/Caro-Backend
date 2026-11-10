package com.whatever.caro.auth.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.AuthService
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.auth.internal.web.request.CompleteRegistrationRequest
import com.whatever.caro.auth.internal.web.response.TokenResponse
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
import org.mockito.BDDMockito.given
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
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
import org.springframework.test.web.servlet.post
import java.time.LocalDate
import java.time.ZoneId

@WebMvcTest(
    controllers = [AuthController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, SecurityFilterAutoConfiguration::class, UserDetailsServiceAutoConfiguration::class],
)
@AutoConfigureMockMvc(addFilters = false)
class AuthRegistrationWebMvcTest : DescribeSpec() {
    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var authService: AuthService

    @MockitoBean lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean lateinit var jwtExceptionFilter: JwtExceptionFilter

    @MockitoBean lateinit var requestResponseLoggingFilter: RequestResponseLoggingFilter

    @MockitoBean lateinit var idempotencyRepository: IdempotencyRepository

    @MockitoBean lateinit var idempotencyProperties: IdempotencyProperties

    init {
        val user = AuthUser(1L, "test-jti", "SUSPENDED")
        val request = CompleteRegistrationRequest("nickname", true)
        val tokens = TokenResponse("access", "refresh")
        beforeEach {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(user, null, emptyList())
        }
        afterEach { SecurityContextHolder.clearContext() }

        describe("회원가입 완료 API 버전") {
            it("버전이 없는 기존 요청은 생년월일 없이 처리한다") {
                given(authService.completeRegistration(user, request, "device", null)).willReturn(tokens)
                mockMvc.post("/auth/complete-registration") {
                    header("Device-Id", "device")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname":"nickname","isTermsAgreed":true}"""
                }.andExpect { status { isOk() } }
                verify(authService).completeRegistration(user, request, "device", null)
            }
            it("1.1은 생년월일을 서비스에 전달한다") {
                val birthDate = LocalDate.of(1990, 1, 1)
                given(authService.completeRegistration(user, request, "device", birthDate, ZoneId.of("Asia/Seoul"))).willReturn(tokens)
                mockMvc.post("/auth/complete-registration") {
                    header("Device-Id", "device")
                    header("API-Version", "1.1")
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname":"nickname","isTermsAgreed":true,"birthDate":"1990-01-01"}"""
                }.andExpect { status { isOk() } }
                verify(authService).completeRegistration(user, request, "device", birthDate, ZoneId.of("Asia/Seoul"))
            }
            it("1.1은 시간대가 없으면 요청을 거부한다") {
                mockMvc.post("/auth/complete-registration") {
                    header("Device-Id", "device")
                    header("API-Version", "1.1")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname":"nickname","isTermsAgreed":true,"birthDate":"1990-01-01"}"""
                }.andExpect { status { isBadRequest() } }
                verifyNoInteractions(authService)
            }
            it("1.1은 생년월일이 없으면 요청을 거부한다") {
                mockMvc.post("/auth/complete-registration") {
                    header("Device-Id", "device")
                    header("API-Version", "1.1")
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname":"nickname","isTermsAgreed":true}"""
                }.andExpect { status { isBadRequest() } }
                verifyNoInteractions(authService)
            }
            it("1.1은 잘못된 날짜 형식을 거부한다") {
                mockMvc.post("/auth/complete-registration") {
                    header("Device-Id", "device")
                    header("API-Version", "1.1")
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"nickname":"nickname","isTermsAgreed":true,"birthDate":"not-a-date"}"""
                }.andExpect { status { isBadRequest() } }
                verifyNoInteractions(authService)
            }
        }
    }
}
