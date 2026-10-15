package com.whatever.caro.card.internal.card

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

/**
 * 애플리케이션 로직과 관계는 없지만, Mobile의 문서 자동화를 위해 추가되었음.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CardApiDocsVersionTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jsonMapper: JsonMapper,
) : DescribeSpec({

    fun apiDocs(
        group: String,
    ): JsonNode =
        mockMvc.get("/v3/api-docs$group")
            .andReturn().response.contentAsString
            .let { jsonMapper.readTree(it) }

    fun createCardsOperation(
        docs: JsonNode,
    ): JsonNode = docs.path("paths").path("/decks/{deckId}/cards").path("post")

    fun hasHeader(
        operation: JsonNode,
        name: String,
    ): Boolean = operation.path("parameters").any { it.path("in").asString() == "header" && it.path("name").asString() == name }

    fun requestBodyRef(
        operation: JsonNode,
    ): String = operation.path("requestBody").path("content").path("application/json").path("schema").path("\$ref").asString()

    describe("POST /decks/{deckId}/cards 버전별 OpenAPI 문서") {
        it("version-1 그룹은 Idempotency-Key 없는 1.0 계약을 그대로 담는다") {
            val operation = createCardsOperation(apiDocs("/version-1"))

            operation.path("operationId").asString() shouldBe "createCards"
            hasHeader(operation, "Idempotency-Key") shouldBe false
            requestBodyRef(operation) shouldEndWith "/CreateCardsRequest"
        }

        it("version-1.1 그룹은 Idempotency-Key 필수와 items 최대 1000개를 담는다") {
            val docs = apiDocs("/version-1.1")
            val operation = createCardsOperation(docs)

            hasHeader(operation, "Idempotency-Key") shouldBe true
            requestBodyRef(operation) shouldEndWith "/CreateCardChunkRequest"
            docs.path("components").path("schemas").path("CreateCardChunkRequest")
                .path("properties").path("items").path("maxItems").asInt() shouldBe 1000
        }

        it("all-version 그룹은 1.0과 합치지 않은 1.1 operation만 담는다") {
            val operation = createCardsOperation(apiDocs("/all-version"))

            operation.path("deprecated").asBoolean() shouldBe false
            hasHeader(operation, "Idempotency-Key") shouldBe true
            requestBodyRef(operation) shouldEndWith "/CreateCardChunkRequest"
        }

        it("그룹 없는 문서에서도 operationId가 createCards로 유지된다") {
            val operation = createCardsOperation(apiDocs(""))

            operation.path("operationId").asString() shouldBe "createCards"
        }
    }
})
