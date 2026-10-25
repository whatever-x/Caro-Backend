package com.whatever.caro.card.internal.library

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.api.library.LibraryCatalogApi
import com.whatever.caro.card.internal.card.CardService
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@ActiveProfiles("library-service-test")
class LibraryServiceTest(
    service: LibraryService,
    catalog: LibraryCatalogApi,
    sources: LibraryDeckRepository,
    content: LibraryCardRepository,
    receipts: LibraryCopyReceiptRepository,
    personal: DeckService,
    jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
    cards: CardService,
) : DescribeSpec({
    val tx = TransactionTemplate(transactionManager)
    val sourceIds = mutableListOf<Long>()

    fun fixture(
        name: String = "토익 단어",
        published: Boolean = true,
    ): LibraryDeck {
        val deck = sources.save(LibraryDeck(name, "단어와 뜻", published))
        sourceIds.add(deck.id)
        content.saveAll(
            listOf(
                LibraryCard(deck.id, "schedule", "일정", 2),
                LibraryCard(deck.id, "apply", "지원하다", 1),
            ),
        )
        return deck
    }

    afterEach {
        jdbc.update("DELETE FROM cards WHERE user_id BETWEEN 8100 AND 8199")
        jdbc.update("DELETE FROM notes WHERE user_id BETWEEN 8100 AND 8199")
        jdbc.update("DELETE FROM decks WHERE user_id BETWEEN 8100 AND 8199")
        jdbc.update("DELETE FROM library_copy_receipts WHERE user_id BETWEEN 8100 AND 8199")
        sourceIds.forEach {
            jdbc.update("DELETE FROM library_cards WHERE library_deck_id = ?", it)
            jdbc.update("DELETE FROM library_decks WHERE id = ?", it)
        }
        sourceIds.clear()
    }

    it("게시된 덱만 정렬하고 빈 목록과 카드 순서를 반환한다") {
        service.list() shouldBe emptyList()
        fixture(published = false)
        val deck = fixture()
        catalog.publishedDeckIds() shouldBe setOf(deck.id)
        service.list().map { it.libraryDeckId } shouldContainExactly listOf(deck.id)
        service.detail(deck.id).cards.map { it.front } shouldContainExactly listOf("apply", "schedule")
    }

    it("새 요청은 독립된 덱을 만들고 삭제와 이름 수정 뒤에도 번호가 누적된다") {
        val deck = fixture()
        val first = service.copy(8100, deck.id, UUID.randomUUID())
        val second = service.copy(8100, deck.id, UUID.randomUUID())
        second.name shouldBe "토익 단어 (2)"
        jdbc.queryForObject("SELECT deck_preset_id FROM decks WHERE id = ?", Long::class.java, second.deckId) shouldBe 1
        jdbc.update("UPDATE decks SET name = '내 이름' WHERE id = ?", second.deckId)
        personal.deleteDeck(8100, DeleteDeckDto(first.deckId))
        val third = service.copy(8100, deck.id, UUID.randomUUID())
        third.name shouldBe "토익 단어 (3)"
        (third.deckId != second.deckId) shouldBe true
        service.copy(8101, deck.id, UUID.randomUUID()).name shouldBe "토익 단어"
    }

    it("원본 게시 종료와 개인 덱 수정 및 삭제 후에도 request replay는 최초 복사 결과를 반환한다") {
        val deck = fixture()
        val key = UUID.randomUUID()
        val copied = service.copy(8102, deck.id, key)
        tx.executeWithoutResult {
            val locked = requireNotNull(sources.lockById(deck.id))
            locked.published = false
            locked.description = "새 설명"
            content.findByLibraryDeckIdOrderByPositionAscIdAsc(deck.id).forEach { it.back = "변경된 뜻" }
        }
        jdbc.queryForObject("SELECT description FROM decks WHERE id = ?", String::class.java, copied.deckId) shouldBe "단어와 뜻"
        jdbc.queryForObject(
            "SELECT JSON_UNQUOTE(JSON_EXTRACT(n.fields, '$.back')) FROM notes n JOIN cards c ON c.note_id=n.id WHERE c.deck_id=? ORDER BY c.id LIMIT 1",
            String::class.java,
            copied.deckId,
        ) shouldBe "지원하다"
        jdbc.update("UPDATE decks SET name = '내 이름', description = '내 설명' WHERE id = ?", copied.deckId)
        service.copy(8102, deck.id, key) shouldBe copied
        personal.deleteDeck(8102, DeleteDeckDto(copied.deckId))
        service.copy(8102, deck.id, key) shouldBe copied
        shouldThrow<BusinessException> { service.copy(8102, deck.id, UUID.randomUUID()) }.errorCode shouldBe LibraryErrorCode.UNAVAILABLE
    }

    it("같은 key의 동시 요청은 같은 결과, 서로 다른 key는 다음 번호를 반환한다") {
        val deck = fixture()
        val pool = Executors.newFixedThreadPool(4)
        fun concurrent(
            keys: List<UUID>,
        ): List<LibraryCopyResponse> {
            val start = CountDownLatch(1)
            val futures = keys.map { key ->
                pool.submit<LibraryCopyResponse> {
                    start.await()
                    service.copy(8103, deck.id, key)
                }
            }
            start.countDown()
            return futures.map { it.get(20, TimeUnit.SECONDS) }
        }
        try {
            val key = UUID.randomUUID()
            concurrent(List(4) { key }).map { it.deckId }.distinct().size shouldBe 1
            concurrent(List(2) { UUID.randomUUID() }).map { it.name }.sorted() shouldContainExactly
                listOf("토익 단어 (2)", "토익 단어 (3)")
            receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(8103, deck.id) shouldBe 3
        } finally {
            pool.shutdownNow()
        }
    }

    it("다른 제공 덱에 같은 key를 사용하면 conflict이며 내용 오류는 번호를 소비하지 않는다") {
        val deck = fixture()
        val other = fixture()
        val key = UUID.randomUUID()
        service.copy(8104, deck.id, key)
        shouldThrow<BusinessException> { service.copy(8104, other.id, key) }.errorCode shouldBe CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT
        tx.executeWithoutResult {
            val bad = content.findByLibraryDeckIdOrderByPositionAscIdAsc(other.id).first()
            bad.front = ""
        }
        shouldThrow<BusinessException> { service.copy(8104, other.id, UUID.randomUUID()) }.errorCode shouldBe LibraryErrorCode.INVALID_CONTENT
        receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(8104, other.id) shouldBe 0
    }

    it("원본 덱에 공유 잠금이 있어도 서로 다른 사용자들이 복사할 수 있다") {
        val deck = fixture()
        val locked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(3)
        val holder = pool.submit {
            tx.executeWithoutResult {
                jdbc.queryForObject("SELECT id FROM library_decks WHERE id = ? FOR SHARE", Long::class.java, deck.id)
                locked.countDown()
                check(release.await(30, TimeUnit.SECONDS))
            }
        }
        val copies = mutableListOf<java.util.concurrent.Future<LibraryCopyResponse>>()
        try {
            check(locked.await(10, TimeUnit.SECONDS))
            listOf(8108L, 8109L).forEach { userId ->
                copies.add(pool.submit<LibraryCopyResponse> { service.copy(userId, deck.id, UUID.randomUUID()) })
            }
            copies.map { it.get(10, TimeUnit.SECONDS).name } shouldContainExactly listOf("토익 단어", "토익 단어")
        } finally {
            release.countDown()
            holder.get(10, TimeUnit.SECONDS)
            pool.shutdown()
            check(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    it("ordinal이 충돌하면 첫 복사의 덱과 카드를 rollback하고 새 transaction에서 다음 번호로 복사한다") {
        val deck = fixture()
        val userId = 8110L
        val existingDeck = personal.createDeck(userId, CreateDeckDto("경합 검증용", "기존 덱"))
        val key = UUID.randomUUID()
        val claimed = CountDownLatch(1)
        val commit = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        val contender = pool.submit {
            tx.executeWithoutResult {
                jdbc.update(
                    """
                    INSERT INTO library_copy_receipts
                        (user_id, request_key, library_deck_id, ordinal, deck_id, name, description, card_count, created_at, updated_at)
                    VALUES (?, ?, ?, 1, ?, '경합 검증용', '기존 덱', 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                    """.trimIndent(),
                    userId,
                    UUID.randomUUID().toString(),
                    deck.id,
                    existingDeck.id,
                )
                claimed.countDown()
                check(commit.await(30, TimeUnit.SECONDS))
            }
        }
        try {
            check(claimed.await(10, TimeUnit.SECONDS))
            val copy = pool.submit<LibraryCopyResponse> { service.copy(userId, deck.id, key) }
            val dirtyRead = TransactionTemplate(transactionManager).apply {
                isolationLevel = TransactionDefinition.ISOLATION_READ_UNCOMMITTED
            }
            await().atMost(10, TimeUnit.SECONDS).until {
                dirtyRead.execute {
                    jdbc.queryForObject(
                        "SELECT COUNT(*) FROM decks WHERE user_id = ? AND name = ?",
                        Long::class.java,
                        userId,
                        deck.name,
                    )
                } == 1L
            }
            commit.countDown()
            contender.get(10, TimeUnit.SECONDS)
            val result = copy.get(20, TimeUnit.SECONDS)
            result.name shouldBe "토익 단어 (2)"
            service.copy(userId, deck.id, key) shouldBe result
            jdbc.queryForList(
                "SELECT ordinal FROM library_copy_receipts WHERE user_id = ? ORDER BY ordinal",
                Long::class.java,
                userId,
            ) shouldContainExactly listOf(1L, 2L)
            jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = ?", Long::class.java, userId) shouldBe 2
            jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE user_id = ?", Long::class.java, userId) shouldBe 2
            jdbc.queryForObject("SELECT COUNT(*) FROM notes WHERE user_id = ?", Long::class.java, userId) shouldBe 2
        } finally {
            commit.countDown()
            contender.get(10, TimeUnit.SECONDS)
            pool.shutdown()
            check(pool.awaitTermination(30, TimeUnit.SECONDS))
        }
    }

    it("카드 생성 중 실패하면 덱과 receipt가 함께 rollback된다") {
        val deck = fixture()
        jdbc.update("""UPDATE card_templates SET required_fields = '["front","back","missing"]' WHERE id = 1""")
        try {
            shouldThrow<BusinessException> {
                service.copy(8105, deck.id, UUID.randomUUID())
            }
            jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8105", Long::class.java) shouldBe 0
            receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(8105, deck.id) shouldBe 0
        } finally {
            jdbc.update("""UPDATE card_templates SET required_fields = '["front","back"]' WHERE id = 1""")
        }
        service.copy(8105, deck.id, UUID.randomUUID()).name shouldBe "토익 단어"
    }

    it("201장 복사는 거부하고 200장 복사 후 개인 덱에 카드를 더 추가할 수 있다") {
        val deck = fixture()
        content.saveAll((3..201).map { LibraryCard(deck.id, "word$it", "meaning$it", it) })
        val key = UUID.randomUUID()
        shouldThrow<BusinessException> {
            service.copy(8107, deck.id, key)
        }.errorCode shouldBe LibraryErrorCode.INVALID_CONTENT
        jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8107", Long::class.java) shouldBe 0
        jdbc.queryForObject("SELECT COUNT(*) FROM library_copy_receipts WHERE user_id = 8107", Long::class.java) shouldBe 0
        content.delete(content.findByLibraryDeckIdOrderByPositionAscIdAsc(deck.id).last())
        val result = service.copy(8107, deck.id, key)
        result.name shouldBe "토익 단어"
        result.cardCount shouldBe 200
        jdbc.queryForObject("SELECT COUNT(*) FROM cards WHERE deck_id = ?", Long::class.java, result.deckId) shouldBe 200
        cards.createCards(
            8107,
            CreateCardsDto(result.deckId, listOf(CreateCardItemDto(1L, mapOf("front" to "extra", "back" to "추가")))),
        )
        jdbc.queryForObject("SELECT card_count FROM decks WHERE id = ?", Long::class.java, result.deckId) shouldBe 201
        content.findByLibraryDeckIdOrderByPositionAscIdAsc(deck.id).size shouldBe 200
    }

    it("누적 번호를 포함해 50자로 자르고 surrogate pair를 나누지 않는다") {
        val longName = "가".repeat(50)
        service.personalName(longName, 2).length shouldBe 50
        service.personalName(longName, 2) shouldBe "가".repeat(46) + " (2)"
        val emojiName = "😀".repeat(25)
        val named = service.personalName(emojiName, 10)
        named.length shouldBe 49
        named.endsWith(" (10)") shouldBe true
    }

    it("회원 탈퇴 시 복사 receipt도 지운다") {
        val deck = fixture()
        service.copy(8106, deck.id, UUID.randomUUID())
        jdbc.update("DELETE FROM cards WHERE user_id = 8106")
        jdbc.update("DELETE FROM notes WHERE user_id = 8106")
        personal.deleteAllByUserId(8106)
        receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(8106, deck.id) shouldBe 0
    }
})
