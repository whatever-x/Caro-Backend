package com.whatever.caro.auth.internal.config

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.Base64

private fun basicAuthHeader(
    user: String,
    pass: String,
): String = "Basic " + Base64.getEncoder().encodeToString("$user:$pass".toByteArray())

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("staging")
@Import(TestcontainersConfiguration::class)
@TestPropertySource(
    properties = [
        "JWT_SECRET=test-secret-key-must-be-at-least-32-bytes-long-for-hs256",
        "GOOGLE_CLIENT_ID=test-google",
        "APPLE_CLIENT_ID=test-apple-1,test-apple-2",
        "DB_URL=jdbc:mysql://dummy:3306/dummy",
        "DB_USERNAME=dummy",
        "DB_PASSWORD=dummy",
        "REDIS_HOST=dummy",
        "REDIS_PASSWORD=dummy",
        "SWAGGER_USERNAME=test-user",
        "SWAGGER_PASSWORD=test-pass",
        "management.server.port=8080",
    ],
)
class SwaggerStagingBasicAuthTest(
    @Autowired private val mockMvc: MockMvc,
) : DescribeSpec({

    describe("Basic Auth 설정으로 인해 credential 없는 요청은 실패한다") {
        it("/swagger 401") {
            val status = mockMvc.get("/swagger").andReturn().response.status
            status shouldBe HttpStatus.UNAUTHORIZED.value()
        }

        it("/v3/api-docs 401") {
            val status = mockMvc.get("/v3/api-docs").andReturn().response.status
            status shouldBe HttpStatus.UNAUTHORIZED.value()
        }

        it("/swagger-ui/index.html 401") {
            val status = mockMvc.get("/swagger-ui/index.html").andReturn().response.status
            status shouldBe HttpStatus.UNAUTHORIZED.value()
        }

        it("응답에 WWW-Authenticate: Basic 헤더 포함") {
            val header = mockMvc.get("/swagger").andReturn().response.getHeader("WWW-Authenticate")
            header shouldContain "Basic"
        }
    }

    describe("올바른 credential이 있는 요청은 성공한다") {
        it("/v3/api-docs 200") {
            val status = mockMvc.get("/v3/api-docs") {
                header("Authorization", basicAuthHeader("test-user", "test-pass"))
            }.andReturn().response.status

            status shouldBe HttpStatus.OK.value()
        }
    }

    describe("잘못된 credential이라면 요청이 실패한다") {
        it("/v3/api-docs 401") {
            val status = mockMvc.get("/v3/api-docs") {
                header("Authorization", basicAuthHeader("test-user", "wrong-pass"))
            }.andReturn().response.status

            status shouldBe HttpStatus.UNAUTHORIZED.value()
        }
    }

    describe("swagger chain 이 비-swagger 경로에 개입하지 않음") {
        it("POST /api/v1/auth/social-login 응답에 WWW-Authenticate Basic 헤더 부재") {
            val response = mockMvc.post("/api/v1/auth/social-login") {
                contentType = org.springframework.http.MediaType.APPLICATION_JSON
                content = "{}"
            }.andReturn().response

            val wwwAuth = response.getHeader("WWW-Authenticate")
            (wwwAuth?.contains("Basic") == true) shouldBe false
        }

        it("/actuator/health 200 (management 포트 통합)") {
            val status = mockMvc.get("/actuator/health").andReturn().response.status
            status shouldBe HttpStatus.OK.value()
        }
    }
})
