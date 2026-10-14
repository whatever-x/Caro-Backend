package com.whatever.caro.card.internal.card

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
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
import io.kotest.matchers.shouldBe
import org.springframework.dao.DataAccessException
import java.time.Instant

@CaroModuleTest(extraIncludes = ["common"])
class CardServiceTest(
    private val cardService: CardService,
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
) : DescribeSpec({

    val userId = 1L
    lateinit var noteType: NoteType
    lateinit var cardTemplate: CardTemplate

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
})
