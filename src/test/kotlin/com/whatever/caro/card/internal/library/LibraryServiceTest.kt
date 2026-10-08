package com.whatever.caro.card.internal.library

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@CaroModuleTest(extraIncludes = ["common"])
@ActiveProfiles("library-service-test")
class LibraryServiceTest(
    service: LibraryService,
    sources: LibraryDeckRepository,
    content: LibraryCardRepository,
    receipts: LibraryCopyReceiptRepository,
    personal: DeckService,
    jdbc: JdbcTemplate,
    transactionManager: PlatformTransactionManager,
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

    it("추가 후 원본 수정과 게시 종료가 개인 카드와 기존 request replay에 영향을 주지 않는다") {
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

    it("카드 생성 중 실패하면 덱과 receipt가 함께 rollback된다") {
        val deck = fixture()
        shouldThrow<BusinessException> {
            tx.executeWithoutResult {
                jdbc.update("""UPDATE card_templates SET required_fields = '["front","back","missing"]' WHERE id = 1""")
                service.copy(8105, deck.id, UUID.randomUUID())
            }
        }
        jdbc.queryForObject("SELECT COUNT(*) FROM decks WHERE user_id = 8105", Long::class.java) shouldBe 0
        receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(8105, deck.id) shouldBe 0
        service.copy(8105, deck.id, UUID.randomUUID()).name shouldBe "토익 단어"
    }

    it("201장 복사는 전체 rollback되고 같은 key로 200장을 다시 요청하면 전체를 복사한다") {
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
