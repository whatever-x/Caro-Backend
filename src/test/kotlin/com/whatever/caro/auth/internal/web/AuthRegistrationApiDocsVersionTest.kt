package com.whatever.caro.auth.internal.web

import com.whatever.caro.TestcontainersConfiguration
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class AuthRegistrationApiDocsVersionTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jsonMapper: JsonMapper,
) : DescribeSpec({
    fun docs(
        group: String,
    ): JsonNode =
        mockMvc.get("/v3/api-docs/$group").andExpect { status { isOk() } }
            .andReturn().response.contentAsString.let { jsonMapper.readTree(it) }

    fun requestRef(
        docs: JsonNode,
    ): String =
        docs.path("paths").path("/auth/complete-registration").path("post")
            .path("requestBody").path("content").path("application/json").path("schema").path("\$ref").asString()

    describe("회원가입 완료 버전별 OpenAPI") {
        it("1.0 문서는 기존 요청 스키마를 가리킨다") {
            val doc = docs("version-1")
            requestRef(doc) shouldEndWith "/CompleteRegistrationRequest"
            doc.path("components").path("schemas").path("CompleteRegistrationRequest")
                .path("properties").has("birthDate") shouldBe false
        }
        it("1.1 문서는 생년월일 필수 요청 스키마를 가리킨다") {
            val doc = docs("version-1.1")
            requestRef(doc) shouldEndWith "/CompleteRegistrationV11Request"
            val schema = doc.path("components").path("schemas").path("CompleteRegistrationV11Request")
            schema.path("properties").path("birthDate").path("format").asString() shouldBe "date"
            schema.path("required").any { it.asString() == "birthDate" } shouldBe true
            schema.path("required").any { it.asString() == "isTermsAgreed" } shouldBe true
        }
    }
})
