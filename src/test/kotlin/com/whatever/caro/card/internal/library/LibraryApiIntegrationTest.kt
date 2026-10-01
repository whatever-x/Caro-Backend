package com.whatever.caro.card.internal.library

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.auth.internal.token.JwtTokenProvider
import com.whatever.caro.user.UserStatus
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class LibraryApiIntegrationTest(
    @Autowired mockMvc: MockMvc,
    @Autowired jwt: JwtTokenProvider,
    @Autowired sources: LibraryDeckRepository,
    @Autowired cards: LibraryCardRepository,
    @Autowired jdbc: JdbcTemplate,
) : DescribeSpec({
    var fixtureId = 0L
    fun auth(
        status: UserStatus = UserStatus.ACTIVE,
    ) = jwt.generateAccessToken(8250, status.name).token
    fun fixture(): LibraryDeck {
        val source = sources.save(LibraryDeck("단어", "뜻", true))
        fixtureId = source.id
        cards.save(LibraryCard(source.id, "apply", "지원하다", 0))
        return source
    }
    afterEach {
        jdbc.update("DELETE FROM cards WHERE user_id = 8250")
        jdbc.update("DELETE FROM notes WHERE user_id = 8250")
        jdbc.update("DELETE FROM decks WHERE user_id = 8250")
        jdbc.update("DELETE FROM library_copy_receipts WHERE user_id = 8250")
        if (fixtureId != 0L) {
            jdbc.update("DELETE FROM library_cards WHERE library_deck_id = ?", fixtureId)
            jdbc.update("DELETE FROM library_decks WHERE id = ?", fixtureId)
        }
        fixtureId = 0L
    }
    it("로그인 없이 조회와 추가할 수 없으며 SUSPENDED 계정은 접근할 수 없다") {
        mockMvc.get("/library/decks") { header("API-Version", "1.0") }.andExpect { status { isUnauthorized() } }
        mockMvc.post("/library/decks/1/copies") { header("API-Version", "1.0") }.andExpect { status { isUnauthorized() } }
        mockMvc.get("/library/decks") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer " + auth(UserStatus.SUSPENDED))
        }.andExpect { status { isForbidden() } }
    }
    it("실제 JWT, version과 API wrapper로 목록 및 읽기 전용 미리보기를 조회한다") {
        val source = fixture()
        val token = auth()
        mockMvc.get("/library/decks") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data[0].libraryDeckId") { value(source.id) }
            jsonPath("$.data[0].cardCount") { value(1) }
        }
        mockMvc.get("/library/decks/" + source.id) {
            header("API-Version", "1.0")
            header("Authorization", "Bearer $token")
        }.andExpect {
            status { isOk() }
            jsonPath("$.data.cards[0].front") { value("apply") }
            jsonPath("$.data.cards[0].back") { value("지원하다") }
        }
        jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8250", Long::class.java) shouldBe 0
    }
    it("body 없는 copy는 201로 개인 덱을 만들고 같은 key는 같은 덱을 반환한다") {
        val source = fixture()
        val token = auth()
        val key = UUID.randomUUID().toString()
        repeat(2) {
            mockMvc.post("/library/decks/" + source.id + "/copies") {
                header("API-Version", "1.0")
                header("Authorization", "Bearer $token")
                header("Idempotency-Key", key)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.data.name") { value("단어") }
                jsonPath("$.data.description") { value("뜻") }
                jsonPath("$.data.cardCount") { value(1) }
                jsonPath("$.data.deckId") { isNumber() }
            }
        }
        jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8250", Long::class.java) shouldBe 1
        val id = jdbc.queryForObject("SELECT id FROM decks WHERE user_id = 8250", Long::class.java)!!
        mockMvc.get("/decks/$id/cards") {
            header("API-Version", "2.0")
            header("Authorization", "Bearer $token")
        }.andExpect { status { isOk() } }
    }
    it("201장의 제공 덱 추가는 422 L002이고 개인 덱을 만들지 않는다") {
        val source = fixture()
        cards.saveAll((1..200).map { LibraryCard(source.id, "word$it", "meaning$it", it) })
        mockMvc.post("/library/decks/" + source.id + "/copies") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer " + auth())
            header("Idempotency-Key", UUID.randomUUID().toString())
        }.andExpect {
            status { isUnprocessableEntity() }
            jsonPath("$.error.code") { value("L002") }
        }
        jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8250", Long::class.java) shouldBe 0
    }

    it("필수 key 누락과 잘못된 key 및 양수가 아닌 ID는 400이다") {
        val token = auth()
        mockMvc.post("/library/decks/1/copies") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer $token")
        }.andExpect { status { isBadRequest() } }
        mockMvc.post("/library/decks/1/copies") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer $token")
            header("Idempotency-Key", "invalid")
        }.andExpect { status { isBadRequest() } }
        mockMvc.get("/library/decks/0") {
            header("API-Version", "1.0")
            header("Authorization", "Bearer $token")
        }.andExpect { status { isBadRequest() } }
    }
})
