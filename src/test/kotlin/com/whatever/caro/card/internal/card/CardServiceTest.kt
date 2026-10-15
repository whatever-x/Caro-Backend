package com.whatever.caro.card.internal.card

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.read.CardResponseDto
import com.whatever.caro.card.internal.card.exception.CardInvalidFieldsException
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteType
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeStrictlyIncreasing
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.env.Environment
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import kotlin.reflect.KFunction
import kotlin.reflect.jvm.javaMethod

@CaroModuleTest(extraIncludes = ["common"])
class CardServiceTest(
    private val cardService: CardService,
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
    private val environment: Environment,
) : DescribeSpec({

    val userId = 1L
    lateinit var noteType: NoteType
    lateinit var cardTemplate: CardTemplate
    lateinit var pairNoteType: NoteType
    lateinit var pairFrontTemplate: CardTemplate
    lateinit var pairBackTemplate: CardTemplate

    beforeSpec {
        noteType = noteTypeRepository.save(NoteType(name = "card-service-test"))
        cardTemplate = cardTemplateRepository.save(
            CardTemplate(
                noteType = noteType,
                requiredFields = listOf("front", "back"),
                template = mapOf("front" to "front", "back" to "back"),
                position = 0,
            ),
        )
        pairNoteType = noteTypeRepository.save(NoteType(name = "card-service-test-pair"))
        pairFrontTemplate = cardTemplateRepository.save(
            CardTemplate(noteType = pairNoteType, requiredFields = listOf("front"), template = mapOf("front" to "front"), position = 0),
        )
        pairBackTemplate = cardTemplateRepository.save(
            CardTemplate(noteType = pairNoteType, requiredFields = listOf("back"), template = mapOf("back" to "back"), position = 1),
        )
    }

    beforeTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
    }

    afterTest {
        cardRepository.deleteAllInBatch()
        noteRepository.deleteAllInBatch()
        deckRepository.deleteAllInBatch()
    }

    afterSpec {
        cardTemplateRepository.delete(cardTemplate)
        noteTypeRepository.delete(noteType)
        cardTemplateRepository.deleteAll(listOf(pairFrontTemplate, pairBackTemplate))
        noteTypeRepository.delete(pairNoteType)
    }

    fun saveDeck(
        ownerId: Long = userId,
        cardCount: Int = 0,
    ): Deck = deckRepository.save(Deck(userId = ownerId, name = "덱", description = "설명", cardCount = cardCount))

    fun chunk(
        deckId: Long,
        fronts: List<String>,
    ) = CreateCardsDto(
        deckId = deckId,
        items = fronts.map { CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to it, "back" to "뒷면 $it")) },
    )

    fun frontTextsInCardIdOrder(
        deckId: Long,
    ): List<String> =
        cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deckId)
            .sortedBy { it.id }
            .map { it.note.fields.getValue("front") }

    fun storedCardCountOf(
        deckId: Long,
    ): Int = deckRepository.findById(deckId).get().cardCount

    fun sessionStatus(): Map<String, Long> =
        jdbcTemplate.query("SHOW SESSION STATUS WHERE Variable_name IN ('Com_insert', 'Questions')") { rs, _ ->
            rs.getString("Variable_name") to rs.getLong("Value")
        }.toMap()

    // 서비스가 바깥 트랜잭션에 참여해 같은 커넥션을 쓰므로, 전후 세션 카운터의 차이에 서비스 본문의 문장만 들어간다.
    fun statementsDuring(
        block: () -> Unit,
    ): Map<String, Long> =
        transactionTemplate.execute {
            val before = sessionStatus()
            block()
            val after = sessionStatus()
            after.mapValues { (name, value) -> value - before.getValue(name) }
        }!!

    // 앞뒤 각 500자 한글·한자로, 문자마다 UTF-8 3바이트라 실제 바이트가 추정치에 가장 가까운 1.1 요청 본문이다.
    fun worstCaseChunk(
        deckId: Long,
    ) = CreateCardsDto(
        deckId = deckId,
        items = (1..1000).map {
            CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to "$it " + "가漢".repeat(250), "back" to "漢가".repeat(250)))
        },
    )

    describe("createCards") {
        it("1,000개를 한 번에 만들면 노트와 카드가 1,000개씩 저장되고 card_count가 1,000 늘어난다") {
            val deck = saveDeck()
            val fronts = (1..1000).map { "단어$it" }

            val result = cardService.createCards(
                userId = userId,
                dto = chunk(deckId = deck.id, fronts = fronts),
            )

            result.items shouldHaveSize 1000
            cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deckId = deck.id) shouldHaveSize 1000
            noteRepository.count() shouldBe 1000L
            storedCardCountOf(deckId = deck.id) shouldBe 1000
        }

        it("응답 순서와 cardId 오름차순이 요청 순서와 같다") {
            val deck = saveDeck()
            val fronts = (1..50).map { "단어$it" }

            val result = cardService.createCards(
                userId = userId,
                dto = chunk(deckId = deck.id, fronts = fronts),
            )

            result.items.map { it.fields.getValue("front") } shouldBe fronts
            result.items.map { it.cardId }.shouldBeStrictlyIncreasing()
            frontTextsInCardIdOrder(deckId = deck.id) shouldBe fronts
        }

        it("카드가 있는 덱에 추가 생성하면 기존 카드가 유지되고 cardCount가 생성 수만큼 증가한다") {
            val deck = saveDeck()
            val first = (1..3).map { "첫 묶음 $it" }
            val second = (1..4).map { "둘째 묶음 $it" }

            val firstResult = cardService.createCards(
                userId = userId,
                dto = chunk(deckId = deck.id, fronts = first),
            )
            val secondResult = cardService.createCards(
                userId = userId,
                dto = chunk(deckId = deck.id, fronts = second),
            )

            secondResult.items.minOf { it.cardId } shouldBeGreaterThan firstResult.items.maxOf { it.cardId }
            frontTextsInCardIdOrder(deckId = deck.id) shouldBe first + second
            storedCardCountOf(deckId = deck.id) shouldBe 7
        }

        it("필수 키가 있으면 빈 문자열도 저장한다") {
            val deck = saveDeck()
            val fields = mapOf("front" to "", "back" to "뒷면")
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = listOf(CreateCardItemDto(noteTypeId = noteType.id, fields = fields)),
            )

            val result = cardService.createCards(userId = userId, dto = dto)

            result.items.single().fields shouldBe fields
            noteRepository.findAll().single().fields shouldBe fields
        }

        it("500자를 초과하는 문자열도 원문 그대로 저장한다") {
            val deck = saveDeck()
            val longBack = "가".repeat(501)
            val fields = mapOf("front" to "앞면", "back" to longBack)
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = listOf(CreateCardItemDto(noteTypeId = noteType.id, fields = fields)),
            )

            val result = cardService.createCards(userId = userId, dto = dto)

            result.items.single().fields shouldBe fields
            noteRepository.findAll().single().fields shouldBe fields
        }

        it("카드 저장 뒤 card_count 갱신이 실패하면 같은 요청의 노트와 카드도 저장되지 않는다") {
            val deck = saveDeck(cardCount = Int.MAX_VALUE) // count 상한을 최대로 두어 실패 유도

            shouldThrow<DataAccessException> {
                cardService.createCards(
                    userId = userId,
                    dto = chunk(deckId = deck.id, fronts = listOf("a", "b", "c")),
                )
            }

            cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deck.id).shouldBeEmpty()
            noteRepository.count() shouldBe 0L
            storedCardCountOf(deck.id) shouldBe Int.MAX_VALUE
        }

        it("필수 필드가 빠진 항목이 하나라도 있으면 CardInvalidFieldsException을 던지고 아무것도 저장하지 않는다") {
            val deck = saveDeck()
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = listOf(
                    CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to "a", "back" to "b")),
                    CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to "c")),
                ),
            )

            shouldThrow<CardInvalidFieldsException> {
                cardService.createCards(userId, dto)
            }

            noteRepository.count() shouldBe 0L
            storedCardCountOf(deck.id) shouldBe 0
        }

        it("삭제된 덱이면 DeckNotFoundException을 던진다") {
            val deck = saveDeck().apply { softDelete(Instant.parse("2026-10-10T00:00:00Z")) }
            deckRepository.save(deck)

            shouldThrow<DeckNotFoundException> {
                cardService.createCards(userId, chunk(deck.id, listOf("a")))
            }
        }

        it("다른 사용자의 덱이면 DeckForbiddenException을 던진다") {
            val deck = saveDeck(ownerId = 2L)

            shouldThrow<DeckForbiddenException> {
                cardService.createCards(userId, chunk(deck.id, listOf("a")))
            }

            noteRepository.count() shouldBe 0L
        }
    }

    describe("createCardChunk") {
        it("1,000개를 요청 순서대로 저장하고 cardId가 증가하며 card_count가 1,000 늘어난다") {
            val deck = saveDeck()
            val fronts = (1..1000).map { "단어$it" }

            val result = cardService.createCardChunk(userId = userId, dto = chunk(deckId = deck.id, fronts = fronts))

            result.items.map { it.fields.getValue("front") } shouldBe fronts
            result.items.map { it.cardId }.shouldBeStrictlyIncreasing()
            frontTextsInCardIdOrder(deckId = deck.id) shouldBe fronts
            noteRepository.count() shouldBe 1000L
            storedCardCountOf(deckId = deck.id) shouldBe 1000
        }

        it("1,000개의 노트와 카드를 INSERT 두 문장으로 저장하고 전체 문장 수는 10 이하다") {
            val deck = saveDeck()
            val fronts = (1..1000).map { "단어$it" }

            val statements = statementsDuring {
                cardService.createCardChunk(userId = userId, dto = chunk(deckId = deck.id, fronts = fronts))
            }

            statements.getValue("Com_insert") shouldBe 2L
            statements.getValue("Questions") shouldBeLessThanOrEqual 10L
            frontTextsInCardIdOrder(deckId = deck.id) shouldBe fronts
        }

        it("앞뒤 각 500자 한글·한자 본문 1,000개는 INSERT 3문장 이하로 요청 순서와 내용 그대로 저장된다") {
            val deck = saveDeck()
            val dto = worstCaseChunk(deckId = deck.id)

            val statements = statementsDuring { cardService.createCardChunk(userId = userId, dto = dto) }

            statements.getValue("Com_insert") shouldBeLessThanOrEqual 3L
            cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deck.id)
                .sortedBy { it.id }
                .map { it.note.fields } shouldBe dto.items.map { it.fields }
        }

        it("노트 유형에 템플릿이 2개면 노트마다 카드 2장이 같은 note_id로 저장되고 응답 순서는 항목 순서 다음 템플릿 순서다") {
            val deck = saveDeck()
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = (1..2).map {
                    CreateCardItemDto(noteTypeId = pairNoteType.id, fields = mapOf("front" to "f$it", "back" to "b$it"))
                },
            )

            val result = cardService.createCardChunk(userId = userId, dto = dto)

            result.items.map { it.fields } shouldBe listOf(
                mapOf("front" to "f1"),
                mapOf("back" to "b1"),
                mapOf("front" to "f2"),
                mapOf("back" to "b2"),
            )
            val stored = cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deck.id).sortedBy { it.id }
            stored.map { it.id } shouldBe result.items.map { it.cardId }
            stored.map { it.cardTemplate.id } shouldBe listOf(pairFrontTemplate.id, pairBackTemplate.id, pairFrontTemplate.id, pairBackTemplate.id)
            stored.map { it.note.fields.getValue("front") } shouldBe listOf("f1", "f1", "f2", "f2")
            stored.map { it.note.id }.distinct() shouldHaveSize 2
            stored[0].note.id shouldBe stored[1].note.id
        }

        it("템플릿 2개 유형 600개는 카드 1,200행도 한 문장이라 INSERT가 2문장이고 note_id 짝과 응답 순서가 요청 순서와 같다") {
            val deck = saveDeck()
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = (1..600).map {
                    CreateCardItemDto(noteTypeId = pairNoteType.id, fields = mapOf("front" to "f$it", "back" to "b$it"))
                },
            )
            val responses = mutableListOf<CardResponseDto>()

            val statements = statementsDuring { responses += cardService.createCardChunk(userId = userId, dto = dto).items }

            statements.getValue("Com_insert") shouldBe 2L
            responses.map { it.fields } shouldBe (1..600).flatMap { listOf(mapOf("front" to "f$it"), mapOf("back" to "b$it")) }
            val stored = cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deck.id).sortedBy { it.id }
            stored.map { it.id } shouldBe responses.map { it.cardId }
            stored.map { it.cardTemplate.id } shouldBe List(600) { listOf(pairFrontTemplate.id, pairBackTemplate.id) }.flatten()
            val noteIds = stored.map { it.note.id }.distinct()
            noteIds shouldHaveSize 600
            stored.map { it.note.id } shouldBe noteIds.flatMap { listOf(it, it) }
            stored.map { it.note.fields } shouldBe dto.items.flatMap { listOf(it.fields, it.fields) }
        }

        it("노트와 카드의 created_at과 updated_at은 모두 같은 값이고 deleted_at은 NULL이다") {
            val deck = saveDeck()

            cardService.createCardChunk(userId = userId, dto = chunk(deckId = deck.id, fronts = listOf("a", "b", "c")))

            val times = jdbcTemplate.queryForList(
                """
                SELECT created_at, updated_at, deleted_at FROM notes WHERE user_id = ?
                UNION ALL
                SELECT created_at, updated_at, deleted_at FROM cards WHERE deck_id = ?
                """.trimIndent(),
                userId,
                deck.id,
            )
            times shouldHaveSize 6
            times.map { it["created_at"] }.distinct() shouldHaveSize 1
            times.map { it["updated_at"] }.distinct() shouldBe times.map { it["created_at"] }.distinct()
            times.map { it["deleted_at"] }.distinct() shouldBe listOf(null)
        }

        it("카드 저장 뒤 card_count 갱신이 실패하면 같은 요청의 노트와 카드도 저장되지 않는다") {
            val deck = saveDeck(cardCount = Int.MAX_VALUE)

            shouldThrow<DataAccessException> {
                cardService.createCardChunk(userId = userId, dto = chunk(deckId = deck.id, fronts = listOf("a", "b", "c")))
            }

            cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deck.id).shouldBeEmpty()
            noteRepository.count() shouldBe 0L
            storedCardCountOf(deck.id) shouldBe Int.MAX_VALUE
        }

        it("필수 필드가 빠진 항목이 하나라도 있으면 CardInvalidFieldsException을 던지고 아무것도 저장하지 않는다") {
            val deck = saveDeck()
            val dto = CreateCardsDto(
                deckId = deck.id,
                items = listOf(
                    CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to "a", "back" to "b")),
                    CreateCardItemDto(noteTypeId = noteType.id, fields = mapOf("front" to "c")),
                ),
            )

            shouldThrow<CardInvalidFieldsException> {
                cardService.createCardChunk(userId = userId, dto = dto)
            }

            noteRepository.count() shouldBe 0L
            storedCardCountOf(deck.id) shouldBe 0
        }

        it("트랜잭션 timeout은 처리 마커 TTL보다 10초 이상 짧고 createCards에는 timeout이 없다") {
            fun transactionalOf(
                method: KFunction<*>,
            ): Transactional = AnnotatedElementUtils.findMergedAnnotation(method.javaMethod!!, Transactional::class.java)!!
            val processingTtl = environment.getRequiredProperty("app.idempotency.processing-ttl", Duration::class.java)

            val chunkTimeout = Duration.ofSeconds(transactionalOf(CardService::createCardChunk).timeout.toLong())

            chunkTimeout shouldBeGreaterThan Duration.ZERO
            chunkTimeout.plusSeconds(10) shouldBeLessThanOrEqualTo processingTtl
            transactionalOf(CardService::createCards).timeout shouldBe TransactionDefinition.TIMEOUT_DEFAULT
        }
    }
})
