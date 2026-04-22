package com.whatever.caro.common.config

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration::class)
@TestPropertySource(
    properties = [
        "JWT_SECRET=test-secret-key-must-be-at-least-32-bytes-long-for-hs256",
        "GOOGLE_CLIENT_ID=test-google-client-id",
        "APPLE_CLIENT_ID=test-apple-client-id",
        "DB_URL=jdbc:mysql://dummy:3306/dummy",
        "DB_USERNAME=dummy",
        "DB_PASSWORD=dummy",
        "REDIS_HOST=dummy",
        "REDIS_PASSWORD=dummy",
    ],
)
class SwaggerDisabledInProdTest(
    @Autowired private val mockMvc: MockMvc,
) : DescribeSpec({

    fun statusOf(
        url: String,
    ): Int = mockMvc.get(url).andReturn().response.status

    describe("prod profile 에서 Swagger UI/api-docs 비활성화") {
        it("/swagger 404") {
            statusOf("/swagger") shouldBe HttpStatus.NOT_FOUND.value()
        }

        it("/swagger-ui/index.html 404") {
            statusOf("/swagger-ui/index.html") shouldBe HttpStatus.NOT_FOUND.value()
        }

        it("/v3/api-docs 404") {
            statusOf("/v3/api-docs") shouldBe HttpStatus.NOT_FOUND.value()
        }
    }
})
