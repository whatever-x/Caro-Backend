package com.whatever.caro.card.internal.card

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.card.internal.card.dto.create.CardType
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.note.NoteRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.post
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CardChunkApiTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val jwtTokenProvider: JwtTokenProvider,
    @Autowired private val deckRepository: DeckRepository,
    @Autowired private val cardRepository: CardRepository,
    @Autowired private val noteRepository: NoteRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jsonMapper: JsonMapper,
) : DescribeSpec({

    val userId = 1L
    val accessToken by lazy { jwtTokenProvider.generateAccessToken(userId = userId, status = "ACTIVE").token }

    beforeSpec {
        jdbcTemplate.update(
            "INSERT INTO note_types (id, name) VALUES (${CardType.BASIC.noteTypeId}, 'front-back') ON DUPLICATE KEY UPDATE id = id",
        )
        jdbcTemplate.update(
            """
            INSERT INTO card_templates (id, note_type_id, required_fields, template, position)
            VALUES (1, ${CardType.BASIC.noteTypeId}, JSON_ARRAY('front', 'back'), JSON_OBJECT('front', 'front', 'back', 'back'), 0)
            ON DUPLICATE KEY UPDATE id = id
            """.trimIndent(),
        )
    }

    afterTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
        deckRepository.deleteAllInBatch()
    }

    fun saveDeck(): Deck = deckRepository.save(Deck(userId = userId, name = "가져온 덱", description = "설명"))

    fun body(
        vararg fronts: String,
    ): String =
        fronts.joinToString(separator = ",", prefix = """{"items": [""", postfix = "]}") {
            """{"cardType": "BASIC", "fields": {"front": "$it", "back": "뒷면 $it"}}"""
        }

    fun requestCardChunkCreation(
        deckId: Long,
        key: String,
        content: String,
    ): MvcResult =
        mockMvc.post("/decks/$deckId/cards") {
            header("Authorization", "Bearer $accessToken")
            header("API-Version", "1.1")
            header("Idempotency-Key", key)
            contentType = MediaType.APPLICATION_JSON
            this.content = content
        }.andReturn()

    fun errorCodeOf(
        result: MvcResult,
    ): String = jsonMapper.readTree(result.response.contentAsString).path("error").path("code").asString()

    fun cardsOf(
        deckId: Long,
    ) = cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deckId = deckId)

    fun cardCountOf(
        deckId: Long,
    ): Int = deckRepository.findById(deckId).get().cardCount

    describe("POST /decks/{deckId}/cards v1.1 최대 카드 chunk 생성") {
        it("1,000개 묶음은 카드 생성 이벤트의 발행 기록까지 저장돼 요청 순서대로 모두 생성된다") {
            // 직렬화 길이를 늘려 4000자를 넘기 위해 8자리 숫자로 설정
            jdbcTemplate.execute("ALTER TABLE cards AUTO_INCREMENT = 10000000")
            val deck = saveDeck()
            val fronts = (1..1000).map { "단어$it" }

            val result = requestCardChunkCreation(
                deckId = deck.id,
                key = UUID.randomUUID().toString(),
                content = body(*fronts.toTypedArray()),
            )

            result.response.status shouldBe 200
            cardsOf(deckId = deck.id).sortedBy { it.id }.map { it.note.fields.getValue("front") } shouldBe fronts
            cardCountOf(deckId = deck.id) shouldBe 1000
        }
    }

    describe("POST /decks/{deckId}/cards v1.1 멱등 처리") {
        it("같은 키와 같은 본문을 다시 보내면 첫 응답을 그대로 받고 카드가 더 생기지 않는다") {
            val deck = saveDeck()
            val key = UUID.randomUUID().toString()
            val content = body("a", "b", "c")

            val first = requestCardChunkCreation(deckId = deck.id, key = key, content = content)
            val replay = requestCardChunkCreation(deckId = deck.id, key = key, content = content)

            first.response.status shouldBe 200
            replay.response.status shouldBe 200
            replay.response.contentAsString shouldBe first.response.contentAsString
            cardsOf(deckId = deck.id) shouldHaveSize 3
            cardCountOf(deckId = deck.id) shouldBe 3
        }

        it("같은 키로 다른 본문을 보내면 409 C009를 반환하고 카드가 더 생기지 않는다") {
            val deck = saveDeck()
            val key = UUID.randomUUID().toString()
            requestCardChunkCreation(deckId = deck.id, key = key, content = body("a", "b"))

            val conflict = requestCardChunkCreation(deckId = deck.id, key = key, content = body("a", "b", "c"))

            conflict.response.status shouldBe 409
            errorCodeOf(result = conflict) shouldBe "C009"
            cardsOf(deckId = deck.id) shouldHaveSize 2
        }

        it("같은 키의 요청이 처리 중이면 409 C010을 반환하고 카드를 만들지 않는다") {
            val deck = saveDeck()
            val key = UUID.randomUUID().toString()
            val processingKey = "idempotency:$userId:$key:processing"
            redisTemplate.opsForValue().set(processingKey, "1", Duration.ofSeconds(30))

            try {
                val inProgress = requestCardChunkCreation(deckId = deck.id, key = key, content = body("a"))

                inProgress.response.status shouldBe 409
                errorCodeOf(result = inProgress) shouldBe "C010"
                cardsOf(deckId = deck.id).shouldBeEmpty()
            } finally {
                redisTemplate.delete(processingKey)
            }
        }

        it("4xx 응답도 저장돼 같은 키로 다시 보내면 덱 상태가 바뀌어도 첫 응답을 받는다") {
            val deck = saveDeck()
            val key = UUID.randomUUID().toString()
            val content = body("a")
            jdbcTemplate.update("UPDATE decks SET deleted_at = NOW(6) WHERE id = ?", deck.id)

            val notFound = requestCardChunkCreation(deckId = deck.id, key = key, content = content)
            jdbcTemplate.update("UPDATE decks SET deleted_at = NULL WHERE id = ?", deck.id)
            val replay = requestCardChunkCreation(deckId = deck.id, key = key, content = content)
            val newKey = requestCardChunkCreation(
                deckId = deck.id,
                key = UUID.randomUUID().toString(),
                content = content,
            )

            notFound.response.status shouldBe 404
            errorCodeOf(result = notFound) shouldBe "D001"
            replay.response.status shouldBe 404
            replay.response.contentAsString shouldBe notFound.response.contentAsString
            newKey.response.status shouldBe 200
            cardsOf(deckId = deck.id) shouldHaveSize 1
        }

        it("5xx 응답은 저장되지 않아 같은 키의 재시도를 다시 처리하고, 실패한 요청의 노트와 카드는 롤백된다") {
            val deck = saveDeck()
            val key = UUID.randomUUID().toString()
            val content = body("a", "b", "c")
            jdbcTemplate.update("UPDATE decks SET card_count = ? WHERE id = ?", Int.MAX_VALUE, deck.id)

            val failed = requestCardChunkCreation(deckId = deck.id, key = key, content = content)

            failed.response.status shouldBe 500
            errorCodeOf(result = failed) shouldBe "C999"
            cardsOf(deckId = deck.id).shouldBeEmpty()
            noteRepository.findAll().filter { it.userId == userId }.shouldBeEmpty()

            jdbcTemplate.update("UPDATE decks SET card_count = 0 WHERE id = ?", deck.id)
            val retried = requestCardChunkCreation(deckId = deck.id, key = key, content = content)

            retried.response.status shouldBe 200
            cardsOf(deckId = deck.id) shouldHaveSize 3
            cardCountOf(deckId = deck.id) shouldBe 3
        }
    }
})
