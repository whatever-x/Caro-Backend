package com.whatever.caro.card.internal.card

import com.whatever.caro.auth.AuthUser
import com.whatever.caro.auth.internal.filter.JwtAuthenticationFilter
import com.whatever.caro.auth.internal.filter.JwtExceptionFilter
import com.whatever.caro.auth.internal.filter.RequestResponseLoggingFilter
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsResponseDto
import com.whatever.caro.card.internal.card.dto.read.CardResponseDto
import com.whatever.caro.common.web.filter.RequestResponseCachingFilter
import com.whatever.caro.common.web.idempotency.IdempotencyProperties
import com.whatever.caro.common.web.idempotency.IdempotencyRepository
import io.kotest.core.spec.style.DescribeSpec
import org.hamcrest.Matchers.equalTo
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Duration
import java.util.UUID

@WebMvcTest(
    controllers = [CardController::class],
    excludeAutoConfiguration = [
        SecurityAutoConfiguration::class,
        SecurityFilterAutoConfiguration::class,
        UserDetailsServiceAutoConfiguration::class,
    ],
)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerWebMvcTest : DescribeSpec() {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var cardService: CardService

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

    @Autowired
    lateinit var webApplicationContext: WebApplicationContext

    // 주입된 mockMvc는 필터를 끈 상태라, 그대로 @Idempotent 핸들러를 호출하면 인터셉터가 요청 본문 사본을 찾지 못해 C999가 된다.
    fun idempotentMockMvc(): MockMvc =
        MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .addFilters<DefaultMockMvcBuilder>(RequestResponseCachingFilter())
            .build()

    fun itemsJson(
        count: Int,
    ): String =
        (1..count).joinToString(separator = ",", prefix = """{"items": [""", postfix = "]}") {
            """{"cardType": "BASIC", "fields": {"front": "f$it", "back": "b$it"}}"""
        }

    fun createCardsDto(
        count: Int,
    ) = CreateCardsDto(
        deckId = 1L,
        items = (1..count).map { CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "f$it", "back" to "b$it")) },
    )

    fun stubCreateCards(
        count: Int,
    ) {
        val result = CreateCardsResponseDto(
            items = (1..count).map { CardResponseDto(cardId = it.toLong(), fields = mapOf("front" to "f$it", "back" to "b$it")) },
        )
        given(cardService.createCards(1L, createCardsDto(count))).willReturn(result)
    }

    fun stubCreateCardChunk(
        count: Int,
    ) {
        val result = CreateCardsResponseDto(
            items = (1..count).map { CardResponseDto(cardId = it.toLong(), fields = mapOf("front" to "f$it", "back" to "b$it")) },
        )
        given(cardService.createCardChunk(1L, createCardsDto(count))).willReturn(result)
    }

    init {
        beforeEach {
            SecurityContextHolder.getContext().authentication =
                UsernamePasswordAuthenticationToken(
                    AuthUser(userId = 1L, jti = "test-jti", status = "ACTIVE"),
                    null,
                    emptyList(),
                )
            given(idempotencyProperties.processingTtl).willReturn(Duration.ofSeconds(30))
            given(idempotencyProperties.responseTtl).willReturn(Duration.ofHours(24))
            given(
                idempotencyRepository.acquireIdempotencyProcessing(anyString(), any(Duration::class.java) ?: Duration.ZERO),
            ).willReturn(true)
        }

        afterEach {
            SecurityContextHolder.clearContext()
        }

        describe("POST /decks/{deckId}/cards - @Valid 검증") {
            it("items 가 비어있으면 400 을 반환한다") {
                mockMvc.post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"items": []}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("item 의 fields 가 비어있으면 400 을 반환한다") {
                mockMvc.post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"items": [{"cardType": "BASIC", "fields": {}}]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }

        describe("POST /decks/{deckId}/cards - 버전 라우팅") {
            it("API-Version 1.0은 Idempotency-Key 없이 200개 생성 요청을 서비스에 전달한다") {
                val itemCount = 200
                stubCreateCards(itemCount)

                mockMvc.post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(itemCount)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(itemCount) }
                }
                verify(cardService).createCards(1L, createCardsDto(itemCount))
            }

            it("API-Version 헤더가 없으면 1.0으로 처리해 Idempotency-Key 없이 생성한다") {
                stubCreateCards(1)

                mockMvc.post("/decks/1/cards") {
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(1)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items[0].cardId") { value(1) }
                }
                verify(cardService).createCards(1L, createCardsDto(1))
            }

            listOf(API_VERSION, API_VERSION_1_1).forEach { version ->
                it("API-Version $version 요청은 서비스가 반환한 카드 목록의 내용과 순서를 유지한다") {
                    if (version == API_VERSION_1_1) stubCreateCardChunk(3) else stubCreateCards(3)
                    val mvc = if (version == API_VERSION_1_1) {
                        idempotentMockMvc()
                    } else {
                        mockMvc
                    }
                    val expectedItems = listOf(
                        mapOf("cardId" to 1, "fields" to mapOf("front" to "f1", "back" to "b1")),
                        mapOf("cardId" to 2, "fields" to mapOf("front" to "f2", "back" to "b2")),
                        mapOf("cardId" to 3, "fields" to mapOf("front" to "f3", "back" to "b3")),
                    )

                    mvc.post("/decks/1/cards") {
                        header(API_VERSION_HEADER, version)
                        if (version == API_VERSION_1_1) {
                            header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        }
                        contentType = MediaType.APPLICATION_JSON
                        content = itemsJson(3)
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.success") { value(true) }
                        jsonPath("$.data.items") { value(equalTo(expectedItems)) }
                    }
                    if (version == API_VERSION_1_1) {
                        verify(cardService).createCardChunk(1L, createCardsDto(3))
                    } else {
                        verify(cardService).createCards(1L, createCardsDto(3))
                    }
                }
            }
        }

        describe("POST /decks/{deckId}/cards - cardType 기본값") {
            listOf(API_VERSION, API_VERSION_1_1).forEach { version ->
                it("API-Version $version 요청은 cardType을 생략하면 noteTypeId 1L로 서비스를 호출한다") {
                    if (version == API_VERSION_1_1) stubCreateCardChunk(1) else stubCreateCards(1)
                    val mvc = if (version == API_VERSION_1_1) idempotentMockMvc() else mockMvc

                    mvc.post("/decks/1/cards") {
                        header(API_VERSION_HEADER, version)
                        if (version == API_VERSION_1_1) {
                            header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                        }
                        contentType = MediaType.APPLICATION_JSON
                        content = """{"items": [{"fields": {"front": "f1", "back": "b1"}}]}"""
                    }.andExpect {
                        status { isOk() }
                        jsonPath("$.success") { value(true) }
                        jsonPath("$.data.items[0].cardId") { value(1) }
                    }
                    if (version == API_VERSION_1_1) {
                        verify(cardService).createCardChunk(1L, createCardsDto(1))
                    } else {
                        verify(cardService).createCards(1L, createCardsDto(1))
                    }
                }
            }
        }

        describe("POST /decks/{deckId}/cards v1.1 - Idempotency-Key 검증") {
            it("Idempotency-Key가 없으면 400 C007을 반환하고 카드를 만들지 않는다") {
                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(1)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(MISSING_HEADER_CODE) }
                }
                verifyNoInteractions(cardService)
            }

            it("Idempotency-Key가 UUID가 아니면 400 C008을 반환하고 카드를 만들지 않는다") {
                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    header(IDEMPOTENCY_KEY_HEADER, "not-a-uuid")
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(1)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_IDEMPOTENCY_KEY_CODE) }
                }
                verifyNoInteractions(cardService)
            }
        }

        describe("POST /decks/{deckId}/cards v1.1 - items 개수 검증") {
            it("1,000개는 생성한다") {
                stubCreateCardChunk(CHUNK_MAX_ITEMS)

                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(CHUNK_MAX_ITEMS)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.data.items.length()") { value(CHUNK_MAX_ITEMS) }
                }
                verify(cardService).createCardChunk(1L, createCardsDto(CHUNK_MAX_ITEMS))
            }

            it("1,001개면 items 필드 오류와 함께 400 C001을 반환하고 카드를 만들지 않는다") {
                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(CHUNK_MAX_ITEMS + 1)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.success") { value(false) }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                    jsonPath("$.error.fieldErrors.length()") { value(1) }
                    jsonPath("$.error.fieldErrors[0].field") { value("items") }
                }
                verifyNoInteractions(cardService)
            }

            it("0개면 items 필드 오류와 함께 400 C001을 반환한다") {
                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = itemsJson(0)
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                    jsonPath("$.error.fieldErrors[0].field") { value("items") }
                }
                verifyNoInteractions(cardService)
            }

            it("항목의 fields가 비어 있으면 400 C001을 반환한다") {
                idempotentMockMvc().post("/decks/1/cards") {
                    header(API_VERSION_HEADER, API_VERSION_1_1)
                    header(IDEMPOTENCY_KEY_HEADER, UUID.randomUUID().toString())
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"items": [{"cardType": "BASIC", "fields": {}}]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
                verifyNoInteractions(cardService)
            }
        }

        describe("PATCH /cards/{id} - @Valid 검증") {
            it("fields 가 비어있으면 400 을 반환한다") {
                mockMvc.patch("/cards/1") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"fields": {}}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }
        }

        describe("DELETE /cards - @Valid 검증") {
            it("cardIds 가 비어있으면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": []}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("cardIds 가 1000개를 초과하면 400 을 반환한다") {
                val ids = (1..1001).joinToString(",")
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [$ids]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("cardIds 에 0 이하가 섞이면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    header("Client-Timezone", "Asia/Seoul")
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [1, 0]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(INVALID_INPUT_CODE) }
                }
            }

            it("Client-Timezone 헤더가 없으면 400 을 반환한다") {
                mockMvc.delete("/cards") {
                    header(API_VERSION_HEADER, API_VERSION)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"cardIds": [1]}"""
                }.andExpect {
                    status { isBadRequest() }
                    jsonPath("$.error.code") { value(MISSING_HEADER_CODE) }
                }
            }
        }
    }

    companion object {
        private const val API_VERSION_HEADER = "API-Version"
        private const val API_VERSION = "1.0"
        private const val API_VERSION_1_1 = "1.1"
        private const val IDEMPOTENCY_KEY_HEADER = "Idempotency-Key"
        private const val CHUNK_MAX_ITEMS = 1000
        private const val INVALID_INPUT_CODE = "C001"
        private const val MISSING_HEADER_CODE = "C007"
        private const val INVALID_IDEMPOTENCY_KEY_CODE = "C008"
    }
}
