package com.whatever.caro.card.internal.card

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.exception.CardLimitExceededException
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZoneId
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@ActiveProfiles("library-service-test")
class CardCapacityIntegrationTest(
    cards: CardService,
    decks: DeckService,
    jdbc: JdbcTemplate,
    deckRepository: DeckRepository,
    transactionManager: PlatformTransactionManager,
    cardEntities: CardRepository,
    notes: NoteRepository,
    templates: CardTemplateRepository,
) : DescribeSpec({
    val userId = 8200L

    fun createDeck(): Long = decks.createDeck(userId, CreateDeckDto("카드 수 제한", "검증용 덱")).id

    fun add(
        deckId: Long,
        count: Int,
    ) = cards.createCards(
        userId,
        CreateCardsDto(deckId, List(count) { CreateCardItemDto(1L, mapOf("front" to "front $it", "back" to "back $it")) }),
    )

    fun assertCount(
        deckId: Long,
        expected: Long,
    ) {
        jdbc.queryForObject("SELECT card_count FROM decks WHERE id = ?", Long::class.java, deckId) shouldBe expected
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM cards WHERE deck_id = ? AND deleted_at IS NULL",
            Long::class.java,
            deckId,
        ) shouldBe expected
    }

    afterEach {
        jdbc.update("DELETE FROM cards WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM notes WHERE user_id = ?", userId)
        jdbc.update("DELETE FROM decks WHERE user_id = ?", userId)
    }

    it("한 번에 200장을 생성하고 같은 덱에 다시 추가하면 누적 200장을 넘을 수 있다") {
        val deckId = createDeck()
        add(deckId, 200)
        assertCount(deckId, 200)
        add(deckId, 200)
        assertCount(deckId, 400)
    }

    it("한 번에 201장을 생성하는 요청과 retry는 카드와 note를 일부 저장하지 않는다") {
        val deckId = createDeck()
        add(deckId, 199)
        repeat(2) { shouldThrow<CardLimitExceededException> { add(deckId, 201) } }
        assertCount(deckId, 199)
        jdbc.queryForObject("SELECT COUNT(*) FROM notes WHERE user_id = ?", Long::class.java, userId) shouldBe 199L
    }

    it("같은 덱에 10장씩 동시 추가하면 두 요청을 모두 저장하고 카드 수를 합산한다") {
        val deckId = createDeck()
        add(deckId, 190)
        assertCount(deckId, 190)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val futures = List(2) {
                pool.submit<Boolean> {
                    start.await()
                    try {
                        add(deckId, 10)
                        true
                    } catch (_: CardLimitExceededException) {
                        false
                    }
                }
            }
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }.count { it } shouldBe 2
            assertCount(deckId, 210)
            jdbc.queryForObject("SELECT COUNT(*) FROM notes WHERE user_id = ?", Long::class.java, userId) shouldBe 210L
        } finally {
            pool.shutdownNow()
        }
    }

    it("저장된 합계가 잘못되어도 새 카드 저장 시 실제 카드 수로 합계를 맞춘다") {
        val deckId = createDeck()
        add(deckId, 199)
        jdbc.update("UPDATE decks SET card_count = 0 WHERE id = ?", deckId)
        add(deckId, 2)
        assertCount(deckId, 201)
    }

    it("기존에 200장을 넘은 덱에도 카드 생성 요청당 200장을 추가할 수 있다") {
        val deckId = createDeck()
        add(deckId, 200)
        val note = notes.save(Note(userId = userId, fields = mapOf("front" to "legacy", "back" to "legacy")))
        cardEntities.save(
            Card(
                cardTemplate = templates.findById(1L).orElseThrow(),
                note = note,
                deck = requireNotNull(deckRepository.findByIdAndDeletedAtIsNull(deckId)),
                userId = userId,
            ),
        )
        add(deckId, 200)
        assertCount(deckId, 401)
    }

    it("카드 생성 후 transaction이 실패하면 증가한 카드 수와 카드 및 note를 함께 rollback한다") {
        val deckId = createDeck()
        add(deckId, 190)
        shouldThrow<IllegalStateException> {
            TransactionTemplate(transactionManager).executeWithoutResult {
                add(deckId, 10)
                error("failure after card creation")
            }
        }
        assertCount(deckId, 190)
        jdbc.queryForObject("SELECT COUNT(*) FROM notes WHERE user_id = ?", Long::class.java, userId) shouldBe 190L
    }

    it("카드를 삭제한 뒤 다시 200장을 추가해도 누적 카드 수를 정확히 반영한다") {
        val deckId = createDeck()
        val created = add(deckId, 200)
        assertCount(deckId, 200)
        cards.deleteCard(userId = userId, dto = DeleteCardDto(setOf(created.items.first().cardId)), timezone = ZoneId.of("UTC"))
        assertCount(deckId, 199)
        add(deckId, 200)
        assertCount(deckId, 399)
    }

    it("이전 카드 수로 읽은 덱의 이름을 수정해도 최신 카드 수를 덮어쓰지 않는다") {
        val deckId = createDeck()
        add(deckId, 190)
        val read = CountDownLatch(1)
        val finishEdit = CountDownLatch(1)
        val pool = Executors.newSingleThreadExecutor()
        try {
            val edit = pool.submit {
                TransactionTemplate(transactionManager).executeWithoutResult {
                    val deck = requireNotNull(deckRepository.findByIdAndDeletedAtIsNull(deckId))
                    read.countDown()
                    check(finishEdit.await(30, TimeUnit.SECONDS))
                    deck.updateDeckInfo("수정한 이름", deck.description)
                }
            }
            check(read.await(30, TimeUnit.SECONDS))
            add(deckId, 10)
            finishEdit.countDown()
            edit.get(30, TimeUnit.SECONDS)
            assertCount(deckId, 200)
        } finally {
            finishEdit.countDown()
            pool.shutdownNow()
        }
    }
})
