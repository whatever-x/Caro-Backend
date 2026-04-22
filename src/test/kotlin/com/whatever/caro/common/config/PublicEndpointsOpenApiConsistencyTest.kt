package com.whatever.caro.common.config

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.auth.internal.config.PublicEndpoints
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.util.AntPathMatcher
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class PublicEndpointsOpenApiConsistencyTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jsonMapper: JsonMapper,
) : DescribeSpec({

    fun fetchApiDocs(): JsonNode =
        mockMvc.get("/v3/api-docs")
            .andReturn().response.contentAsString
            .let { jsonMapper.readTree(it) }

    fun isEmptyArray(
        node: JsonNode?,
    ): Boolean = node != null && node.isArray && node.isEmpty

    val antMatcher = AntPathMatcher()

    describe("PublicEndpoints.AUTH ↔ OpenAPI security:[] 양방향 정합성") {

        it("PublicEndpoints.AUTH 의 모든 경로는 security:[]로 직렬화된다 (@PublicApi 누락 확인)") {
            val apiDocs = fetchApiDocs()

            PublicEndpoints.AUTH.forEach { registeredPath ->
                val pathNode = apiDocs.get("paths")?.get(registeredPath)
                pathNode.shouldNotBeNull()

                pathNode.properties().forEach { (method, operation) ->
                    operation.get("security").shouldBeEmpty()
                }
            }
        }

        it("security:[]인 모든 경로는 PublicEndpoints.AUTH 에 매칭된다 (PATTERNS 등록 누락 확인)") {
            val apiDocs = fetchApiDocs()
            val pathsNode = apiDocs.get("paths")
            pathsNode.shouldNotBeNull()

            pathsNode.properties().forEach { (documentedPath, pathNode) ->
                pathNode.properties().forEach { (method, operation) ->
                    val security = operation.get("security") ?: return@forEach
                    if (!isEmptyArray(security)) return@forEach

                    PublicEndpoints.AUTH
                        .firstOrNull { pattern -> antMatcher.match(pattern, documentedPath) }
                        .shouldNotBeNull()
                }
            }
        }
    }
})
