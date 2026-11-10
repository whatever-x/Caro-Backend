package com.whatever.caro.user.internal.web

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.internal.MyInfo
import com.whatever.caro.user.internal.UserService
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
import org.springframework.test.web.servlet.put

@WebMvcTest(
    controllers = [UserController::class],
    excludeAutoConfiguration = [SecurityAutoConfiguration::class, SecurityFilterAutoConfiguration::class, UserDetailsServiceAutoConfiguration::class],
)
@AutoConfigureMockMvc(addFilters = false)
class UserAgeControllerWebMvcTest : DescribeSpec() {
    @Autowired lateinit var mockMvc: MockMvc

    @MockitoBean lateinit var userService: UserService

    @MockitoBean lateinit var jwtAuthenticationFilter: JwtAuthenticationFilter

    @MockitoBean lateinit var jwtExceptionFilter: JwtExceptionFilter

    @MockitoBean lateinit var requestResponseLoggingFilter: RequestResponseLoggingFilter

    @MockitoBean lateinit var idempotencyRepository: IdempotencyRepository

    @MockitoBean lateinit var idempotencyProperties: IdempotencyProperties

    init {
        beforeEach {
            SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(
                AuthUser(userId = 1L, jti = "test", status = "ACTIVE"),
                null,
                emptyList(),
            )
        }
        afterEach { SecurityContextHolder.clearContext() }

        describe("GET /users/me/info") {
            listOf(true, false).forEach { adult ->
                it("성인 여부 $adult 값을 isAdult 필드로 반환한다") {
                    given(userService.getUserInfo(1L)).willReturn(MyInfo("nickname", null, SocialProvider.GOOGLE, adult))
                    mockMvc.get("/users/me/info") {
                        header("API-Version", "1.0")
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.data.isAdult") { value(adult) }
                    }
                }
            }
        }

        describe("PUT /users/me/age") {
            it("재확인 기능이 보류되어 외부에 노출되지 않는다") {
                mockMvc.put("/users/me/age") {
                    header("API-Version", "1.0")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"birthDate":"1990-01-01"}"""
                }.andExpect { status { isNotFound() } }
            }
        }
    }
}
