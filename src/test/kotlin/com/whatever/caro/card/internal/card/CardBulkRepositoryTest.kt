package com.whatever.caro.card.internal.card

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteType
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@CaroModuleTest(extraIncludes = ["common"])
class CardBulkRepositoryTest(
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
) : DescribeSpec({

    val userId = 7_101L
    val utcDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS").withZone(ZoneOffset.UTC)
    lateinit var noteType: NoteType
    lateinit var frontTemplate: CardTemplate
    lateinit var backTemplate: CardTemplate
    lateinit var deck: Deck

    fun saveTemplate(
        position: Int,
    ): CardTemplate =
        cardTemplateRepository.save(
            CardTemplate(
                noteType = noteType,
                requiredFields = listOf("front", "back"),
                template = mapOf("front" to "front", "back" to "back"),
                position = position,
            ),
        )

    fun saveNoteIds(
        count: Int,
    ): List<Long> =
        noteRepository.saveAll((1..count).map { Note(userId = userId, fields = mapOf("front" to "f$it", "back" to "b$it")) })
            .map { it.id }

    fun rowsFor(
        noteIds: List<Long>,
    ): List<CardInsertRow> =
        noteIds.flatMap { noteId ->
            listOf(frontTemplate, backTemplate).map { template ->
                CardInsertRow(cardTemplateId = template.id, noteId = noteId, deckId = deck.id, userId = userId)
            }
        }

    fun storedRows(): List<Pair<Long, CardInsertRow>> =
        jdbcTemplate.query(
            "SELECT id, card_template_id, note_id, deck_id, user_id FROM cards WHERE deck_id = ? ORDER BY id",
            { rs, _ ->
                rs.getLong("id") to CardInsertRow(
                    cardTemplateId = rs.getLong("card_template_id"),
                    noteId = rs.getLong("note_id"),
                    deckId = rs.getLong("deck_id"),
                    userId = rs.getLong("user_id"),
                )
            },
            deck.id,
        )

    // 컬럼 목록을 고정하지 않아야 나중에 추가된 컬럼도 JPA 저장값과 비교된다.
    fun storedColumns(
        id: Long,
    ): Map<String, Any?> = jdbcTemplate.queryForMap("SELECT * FROM cards WHERE id = ?", id) - setOf("id", "note_id", "created_at", "updated_at")

    fun comInsert(): Long = jdbcTemplate.queryForObject("SHOW SESSION STATUS LIKE 'Com_insert'") { rs, _ -> rs.getLong("Value") }!!

    beforeSpec {
        noteType = noteTypeRepository.save(NoteType(name = "card-bulk-repository-test"))
        frontTemplate = saveTemplate(position = 0)
        backTemplate = saveTemplate(position = 1)
    }

    beforeTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
        deck = deckRepository.save(Deck(userId = userId, name = "덱", description = "설명"))
    }

    afterTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
        deckRepository.delete(deck)
    }

    afterSpec {
        cardTemplateRepository.deleteAll(listOf(frontTemplate, backTemplate))
        noteTypeRepository.delete(noteType)
    }

    describe("insertAll") {
        it("노트 유형에 템플릿이 2개면 카드 키가 입력 순서이고 각 카드의 note_id와 card_template_id가 입력 행과 같다") {
            val rows = rowsFor(saveNoteIds(3))

            val keys = transactionTemplate.execute { cardRepository.insertAll(rows, Instant.now(clock)) }!!

            keys.zipWithNext { previous, next -> next - previous }.toSet() shouldBe setOf(1L)
            storedRows() shouldBe keys.zip(rows)
        }

        it("JPA로 저장한 카드와 id, note_id, created_at, updated_at을 뺀 모든 컬럼이 같다") {
            val (jpaNote, jdbcNote) = noteRepository.saveAll((1..2).map { Note(userId = userId, fields = mapOf("front" to "f$it")) })
            val jpaId = cardRepository.saveAll(listOf(Card(cardTemplate = frontTemplate, note = jpaNote, deck = deck, userId = userId))).single().id

            val jdbcId = transactionTemplate.execute {
                cardRepository.insertAll(
                    listOf(CardInsertRow(cardTemplateId = frontTemplate.id, noteId = jdbcNote.id, deckId = deck.id, userId = userId)),
                    Instant.now(clock),
                )
            }!!.single()

            storedColumns(jdbcId) shouldBe storedColumns(jpaId)
        }

        it("1,000행은 INSERT 한 문장으로 저장된다") {
            val rows = rowsFor(saveNoteIds(500))

            val (inserts, keys) = transactionTemplate.execute {
                val before = comInsert()
                val keys = cardRepository.insertAll(rows, Instant.now(clock))
                (comInsert() - before) to keys
            }!!

            inserts shouldBe 1L
            storedRows() shouldBe keys.zip(rows)
        }

        it("created_at과 updated_at은 now의 UTC 기준 DATETIME 값이고, JPA로 읽은 시각이 now와 같으며 deleted_at은 NULL이다") {
            val now = Instant.now(clock)

            val id = transactionTemplate.execute { cardRepository.insertAll(rowsFor(saveNoteIds(1)).take(1), now) }!!.single()

            val times = jdbcTemplate.queryForMap(
                """
                SELECT DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s.%f') AS created_at,
                       DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s.%f') AS updated_at,
                       deleted_at
                FROM cards WHERE id = ?
                """.trimIndent(),
                id,
            )
            times["created_at"] shouldBe utcDateTime.format(now)
            times["updated_at"] shouldBe utcDateTime.format(now)
            times["deleted_at"] shouldBe null
            val card = cardRepository.findById(id).get()
            card.createdAt shouldBe now
            card.updatedAt shouldBe now
        }
    }
})
