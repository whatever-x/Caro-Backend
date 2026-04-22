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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(TestcontainersConfiguration::class)
class SwaggerLocalAccessTest(
    @Autowired private val mockMvc: MockMvc,
) : DescribeSpec({

    fun statusOf(
        url: String,
    ): Int = mockMvc.get(url).andReturn().response.status

    describe("local 에서 Swagger HTTP 응답") {
        it("/swagger → 3xx 리다이렉트") {
            statusOf("/swagger") shouldBe HttpStatus.FOUND.value()
        }

        it("/swagger-ui/index.html → 200") {
            statusOf("/swagger-ui/index.html") shouldBe HttpStatus.OK.value()
        }

        it("/v3/api-docs → 200") {
            statusOf("/v3/api-docs") shouldBe HttpStatus.OK.value()
        }
    }
})
