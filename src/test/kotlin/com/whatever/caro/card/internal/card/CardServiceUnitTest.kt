package com.whatever.caro.card.internal.card

import com.whatever.caro.card.api.event.CardsCreatedEvent
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.dto.update.UpdateCardDto
import com.whatever.caro.card.internal.card.exception.CardForbiddenException
import com.whatever.caro.card.internal.card.exception.CardInvalidFieldsException
import com.whatever.caro.card.internal.card.exception.CardNotFoundException
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteType
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNoTemplatesException
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CardServiceUnitTest :
    DescribeSpec({

        val cardRepository = mockk<CardRepository>()
        val noteRepository = mockk<NoteRepository>()
        val deckRepository = mockk<DeckRepository>()
        val noteTypeRepository = mockk<NoteTypeRepository>()
        val cardTemplateRepository = mockk<CardTemplateRepository>()
        val eventPublisher = mockk<org.springframework.context.ApplicationEventPublisher>(relaxed = true)
        val fixedNow = Instant.parse("2026-06-08T00:00:00.00Z")
        val clock = Clock.fixed(fixedNow, ZoneId.of("UTC"))
        val cardService = CardService(
            cardRepository = cardRepository,
            noteRepository = noteRepository,
            deckRepository = deckRepository,
            noteTypeRepository = noteTypeRepository,
            cardTemplateRepository = cardTemplateRepository,
            eventPublisher = eventPublisher,
            clock = clock,
        )

        beforeEach {
            clearMocks(
                cardRepository,
                noteRepository,
                deckRepository,
                noteTypeRepository,
                cardTemplateRepository,
                eventPublisher,
            )
        }

        fun setId(
            entity: Any,
            id: Long,
        ) {
            val field = entity::class.java.getDeclaredField("id")
            field.isAccessible = true
            field.set(entity, id)
        }

        fun newDeck(
            id: Long,
            userId: Long,
            cardCount: Int = 0,
        ): Deck {
            val deck = Deck(userId = userId, name = "덱", description = "설명", cardCount = cardCount)
            setId(deck, id)
            return deck
        }

        fun newNote(
            id: Long,
            userId: Long,
            fields: Map<String, String>,
        ): Note {
            val note = Note(userId = userId, fields = fields)
            setId(note, id)
            return note
        }

        fun newNoteType(
            id: Long,
            name: String = "Basic",
        ): NoteType {
            val noteType = NoteType(name = name)
            setId(noteType, id)
            return noteType
        }

        fun newTemplate(
            id: Long,
            noteType: NoteType,
            requiredFields: List<String>,
            position: Int = 0,
        ): CardTemplate {
            val template = CardTemplate(
                noteType = noteType,
                requiredFields = requiredFields,
                template = emptyMap(),
                position = position,
            )
            setId(template, id)
            return template
        }

        fun newCard(
            id: Long,
            template: CardTemplate,
            note: Note,
            deck: Deck,
            userId: Long,
        ): Card {
            val card = Card(cardTemplate = template, note = note, deck = deck, userId = userId)
            setId(card, id)
            return card
        }

        describe("createCards") {
            it("덱과 템플릿이 유효하면 카드들을 생성하고 cardCount를 증가시키며 CardsCreatedEvent를 발행한다") {
                val userId = 1L
                val deckId = 10L
                val noteTypeId = 1L
                val deck = newDeck(id = deckId, userId = userId, cardCount = 5)
                val noteType = newNoteType(id = noteTypeId)
                val frontTpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"), position = 0)
                val backTpl = newTemplate(id = 101L, noteType = noteType, requiredFields = listOf("back"), position = 1)
                val dto = CreateCardsDto(
                    deckId = deckId,
                    items = listOf(
                        CreateCardItemDto(noteTypeId = noteTypeId, fields = mapOf("front" to "apple", "back" to "사과")),
                    ),
                )

                every { deckRepository.findByIdAndDeletedAtIsNull(deckId) } returns deck
                every { noteTypeRepository.findAllByIdIn(setOf(noteTypeId)) } returns listOf(noteType)
                every {
                    cardTemplateRepository.findAllByNoteTypeIdIn(setOf(noteTypeId))
                } returns listOf(frontTpl, backTpl)
                every { noteRepository.saveAll(any<Iterable<Note>>()) } answers {
                    val saved = firstArg<Iterable<Note>>().toList()
                    saved.forEachIndexed { i, note -> setId(note, (200 + i).toLong()) }
                    saved
                }
                every { cardRepository.saveAll(any<Iterable<Card>>()) } answers {
                    val saved = firstArg<Iterable<Card>>().toList()
                    saved.forEachIndexed { i, card -> setId(card, (300 + i).toLong()) }
                    saved
                }

                val result = cardService.createCards(userId, dto)

                result.items shouldHaveSize 2
                result.items[0].cardId shouldBe 300L
                result.items[0].fields shouldBe mapOf("front" to "apple")
                result.items[1].cardId shouldBe 301L
                result.items[1].fields shouldBe mapOf("back" to "사과")
                deck.cardCount shouldBe 7
                verify {
                    eventPublisher.publishEvent(
                        CardsCreatedEvent(cardIds = listOf(300L, 301L), deckId = deckId, userId = userId),
                    )
                }
            }

            it("여러 카드 묶음 요청 시 묶음마다 템플릿 수만큼 카드를 생성한다") {
                val userId = 1L
                val deckId = 10L
                val noteTypeId = 1L
                val deck = newDeck(id = deckId, userId = userId, cardCount = 0)
                val noteType = newNoteType(id = noteTypeId)
                val frontTpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"), position = 0)
                val backTpl = newTemplate(id = 101L, noteType = noteType, requiredFields = listOf("back"), position = 1)
                val dto = CreateCardsDto(
                    deckId = deckId,
                    items = listOf(
                        CreateCardItemDto(noteTypeId = noteTypeId, fields = mapOf("front" to "f1", "back" to "b1")),
                        CreateCardItemDto(noteTypeId = noteTypeId, fields = mapOf("front" to "f2", "back" to "b2")),
                    ),
                )

                every { deckRepository.findByIdAndDeletedAtIsNull(deckId) } returns deck
                every { noteTypeRepository.findAllByIdIn(setOf(noteTypeId)) } returns listOf(noteType)
                every {
                    cardTemplateRepository.findAllByNoteTypeIdIn(setOf(noteTypeId))
                } returns listOf(frontTpl, backTpl)
                every { noteRepository.saveAll(any<Iterable<Note>>()) } answers {
                    val saved = firstArg<Iterable<Note>>().toList()
                    saved.forEachIndexed { i, note -> setId(note, (200 + i).toLong()) }
                    saved
                }
                every { cardRepository.saveAll(any<Iterable<Card>>()) } answers {
                    val saved = firstArg<Iterable<Card>>().toList()
                    saved.forEachIndexed { i, card -> setId(card, (300 + i).toLong()) }
                    saved
                }

                val result = cardService.createCards(userId, dto)

                result.items shouldHaveSize 4
                deck.cardCount shouldBe 4
                result.items.map { it.cardId } shouldContainExactlyInAnyOrder listOf(300L, 301L, 302L, 303L)
            }

            it("덱이 없으면 DeckNotFoundException 을 던진다") {
                every { deckRepository.findByIdAndDeletedAtIsNull(99L) } returns null

                shouldThrow<DeckNotFoundException> {
                    cardService.createCards(
                        userId = 1L,
                        dto = CreateCardsDto(
                            deckId = 99L,
                            items = listOf(CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "x"))),
                        ),
                    )
                }
            }

            it("덱 소유자가 아니면 DeckForbiddenException 을 던진다") {
                val deck = newDeck(id = 10L, userId = 2L)
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck

                shouldThrow<DeckForbiddenException> {
                    cardService.createCards(
                        userId = 1L,
                        dto = CreateCardsDto(
                            deckId = 10L,
                            items = listOf(CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "x"))),
                        ),
                    )
                }
            }

            it("요청한 노트 타입 중 일부가 존재하지 않으면 NoteTypeNotFoundException 을 던진다") {
                val deck = newDeck(id = 10L, userId = 1L)
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck
                every { noteTypeRepository.findAllByIdIn(setOf(1L, 999L)) } returns listOf(newNoteType(id = 1L))

                shouldThrow<NoteTypeNotFoundException> {
                    cardService.createCards(
                        userId = 1L,
                        dto = CreateCardsDto(
                            deckId = 10L,
                            items = listOf(
                                CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "x")),
                                CreateCardItemDto(noteTypeId = 999L, fields = mapOf("front" to "y")),
                            ),
                        ),
                    )
                }
            }

            it("노트 타입에 템플릿이 하나도 없으면 NoteTypeNoTemplatesException 을 던진다") {
                val deck = newDeck(id = 10L, userId = 1L)
                val noteType = newNoteType(id = 1L)
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck
                every { noteTypeRepository.findAllByIdIn(setOf(1L)) } returns listOf(noteType)
                every { cardTemplateRepository.findAllByNoteTypeIdIn(setOf(1L)) } returns emptyList()

                shouldThrow<NoteTypeNoTemplatesException> {
                    cardService.createCards(
                        userId = 1L,
                        dto = CreateCardsDto(
                            deckId = 10L,
                            items = listOf(CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "x"))),
                        ),
                    )
                }
            }

            it("템플릿의 필수 필드가 누락되면 CardInvalidFieldsException 을 던진다") {
                val deck = newDeck(id = 10L, userId = 1L)
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front", "back"))
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck
                every { noteTypeRepository.findAllByIdIn(setOf(1L)) } returns listOf(noteType)
                every { cardTemplateRepository.findAllByNoteTypeIdIn(setOf(1L)) } returns listOf(tpl)

                shouldThrow<CardInvalidFieldsException> {
                    cardService.createCards(
                        userId = 1L,
                        dto = CreateCardsDto(
                            deckId = 10L,
                            items = listOf(CreateCardItemDto(noteTypeId = 1L, fields = mapOf("front" to "x"))),
                        ),
                    )
                }
            }
        }

        describe("getCard") {
            it("자신의 카드면 템플릿 필드 기준 응답을 반환한다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "apple", "back" to "사과"))
                val deck = newDeck(id = 10L, userId = userId)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                val result = cardService.getCard(userId = userId, cardId = 300L)

                result.cardId shouldBe 300L
                result.fields shouldBe mapOf("front" to "apple")
            }

            it("템플릿 필드가 노트에 없으면 빈 문자열로 채워진다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front", "back"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "apple"))
                val deck = newDeck(id = 10L, userId = userId)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                val result = cardService.getCard(userId = userId, cardId = 300L)

                result.fields shouldBe mapOf("front" to "apple", "back" to "")
            }

            it("카드가 없으면 CardNotFoundException 을 던진다") {
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(999L) } returns null

                shouldThrow<CardNotFoundException> {
                    cardService.getCard(userId = 1L, cardId = 999L)
                }
            }

            it("다른 유저의 카드면 CardForbiddenException 을 던진다") {
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = 2L, fields = mapOf("front" to "x"))
                val deck = newDeck(id = 10L, userId = 2L)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = 2L)
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                shouldThrow<CardForbiddenException> {
                    cardService.getCard(userId = 1L, cardId = 300L)
                }
            }
        }

        describe("getCardsByDeck") {
            it("자신의 덱이면 카드 목록을 반환한다") {
                val userId = 1L
                val deck = newDeck(id = 10L, userId = userId)
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note1 = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val note2 = newNote(id = 201L, userId = userId, fields = mapOf("front" to "b"))
                val cards = listOf(
                    newCard(id = 300L, template = tpl, note = note1, deck = deck, userId = userId),
                    newCard(id = 301L, template = tpl, note = note2, deck = deck, userId = userId),
                )

                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck
                every { cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(10L) } returns cards

                val result = cardService.getCardsByDeck(userId = userId, deckId = 10L)

                result shouldHaveSize 2
                result[0].cardId shouldBe 300L
                result[0].fields shouldBe mapOf("front" to "a")
                result[1].cardId shouldBe 301L
                result[1].fields shouldBe mapOf("front" to "b")
            }

            it("덱에 카드가 없으면 빈 목록을 반환한다") {
                val deck = newDeck(id = 10L, userId = 1L)
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck
                every { cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(10L) } returns emptyList()

                cardService.getCardsByDeck(userId = 1L, deckId = 10L).shouldBeEmpty()
            }

            it("덱이 없으면 DeckNotFoundException 을 던진다") {
                every { deckRepository.findByIdAndDeletedAtIsNull(99L) } returns null

                shouldThrow<DeckNotFoundException> {
                    cardService.getCardsByDeck(userId = 1L, deckId = 99L)
                }
            }

            it("다른 유저의 덱이면 DeckForbiddenException 을 던진다") {
                val deck = newDeck(id = 10L, userId = 2L)
                every { deckRepository.findByIdAndDeletedAtIsNull(10L) } returns deck

                shouldThrow<DeckForbiddenException> {
                    cardService.getCardsByDeck(userId = 1L, deckId = 10L)
                }
            }
        }

        describe("updateCard") {
            it("필수 필드만 포함되면 노트의 fields 가 머지된다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front", "back"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "apple", "back" to "사과"))
                val deck = newDeck(id = 10L, userId = userId)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                val result = cardService.updateCard(
                    userId = userId,
                    dto = UpdateCardDto(cardId = 300L, fields = mapOf("front" to "banana")),
                )

                result.cardId shouldBe 300L
                result.fields shouldBe mapOf("front" to "banana", "back" to "사과")
                note.fields shouldBe mapOf("front" to "banana", "back" to "사과")
            }

            it("카드가 없으면 CardNotFoundException 을 던진다") {
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(999L) } returns null

                shouldThrow<CardNotFoundException> {
                    cardService.updateCard(
                        userId = 1L,
                        dto = UpdateCardDto(cardId = 999L, fields = mapOf("front" to "x")),
                    )
                }
            }

            it("다른 유저의 카드면 CardForbiddenException 을 던진다") {
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = 2L, fields = mapOf("front" to "x"))
                val deck = newDeck(id = 10L, userId = 2L)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = 2L)
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                shouldThrow<CardForbiddenException> {
                    cardService.updateCard(
                        userId = 1L,
                        dto = UpdateCardDto(cardId = 300L, fields = mapOf("front" to "x")),
                    )
                }
            }

            it("템플릿에 없는 필드가 포함되면 CardInvalidFieldsException 을 던진다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "x"))
                val deck = newDeck(id = 10L, userId = userId)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                shouldThrow<CardInvalidFieldsException> {
                    cardService.updateCard(
                        userId = userId,
                        dto = UpdateCardDto(cardId = 300L, fields = mapOf("hint" to "힌트")),
                    )
                }
            }
        }

        describe("deleteCard") {
            val clientTimezone = ZoneId.of("Asia/Seoul")

            it("같은 노트의 다른 카드가 남아있으면 카드만 soft delete 하고 cardCount 를 1 감소시킨다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val deck = newDeck(id = 10L, userId = userId, cardCount = 3)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card
                every { cardRepository.countByNoteIdAndDeletedAtIsNullAndIdNot(200L, 300L) } returns 1L

                val result = cardService.deleteCard(
                    userId = userId,
                    timezone = clientTimezone,
                    dto = DeleteCardDto(cardId = 300L),
                )

                result.cardId shouldBe 300L
                card.isDeleted.shouldBeTrue()
                note.isDeleted.shouldBeFalse()
                deck.cardCount shouldBe 2
                verify {
                    eventPublisher.publishEvent(
                        CardsDeletedEvent(
                            deckId = 10L,
                            deletedCount = 1,
                            userId = userId,
                            deletedCardIds = setOf(card.id),
                            deletedAt = fixedNow,
                            clientTimezone = clientTimezone,
                        ),
                    )
                }
            }

            it("같은 노트의 마지막 카드면 노트도 함께 soft delete 한다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val deck = newDeck(id = 10L, userId = userId, cardCount = 1)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card
                every { cardRepository.countByNoteIdAndDeletedAtIsNullAndIdNot(200L, 300L) } returns 0L

                cardService.deleteCard(userId, clientTimezone, DeleteCardDto(cardId = 300L))

                card.isDeleted.shouldBeTrue()
                note.isDeleted.shouldBeTrue()
                deck.cardCount shouldBe 0
            }

            it("cardCount 가 이미 0 이면 음수로 내려가지 않고 0 으로 유지된다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val deck = newDeck(id = 10L, userId = userId, cardCount = 0)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card
                every { cardRepository.countByNoteIdAndDeletedAtIsNullAndIdNot(200L, 300L) } returns 0L

                cardService.deleteCard(userId, clientTimezone, DeleteCardDto(cardId = 300L))

                deck.cardCount shouldBe 0
            }

            it("카드가 없으면 CardNotFoundException 을 던진다") {
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(999L) } returns null

                shouldThrow<CardNotFoundException> {
                    cardService.deleteCard(userId = 1L, timezone = clientTimezone, dto = DeleteCardDto(cardId = 999L))
                }
            }

            it("다른 유저의 카드면 CardForbiddenException 을 던진다") {
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val note = newNote(id = 200L, userId = 2L, fields = mapOf("front" to "x"))
                val deck = newDeck(id = 10L, userId = 2L)
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = 2L)
                every { cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(300L) } returns card

                shouldThrow<CardForbiddenException> {
                    cardService.deleteCard(userId = 1L, timezone = clientTimezone, dto = DeleteCardDto(cardId = 300L))
                }
            }
        }

        describe("getCardsByIds") {
            it("userId가 소유한 cardId들을 cardId 기준 맵으로 반환하고 템플릿 필드로 콘텐츠를 채운다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val deck = newDeck(id = 10L, userId = userId)
                val note1 = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val note2 = newNote(id = 201L, userId = userId, fields = mapOf("front" to "b"))
                val cards = listOf(
                    newCard(id = 300L, template = tpl, note = note1, deck = deck, userId = userId),
                    newCard(id = 301L, template = tpl, note = note2, deck = deck, userId = userId),
                )

                every {
                    cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(listOf(300L, 301L), userId)
                } returns cards

                val result = cardService.getCardsByIds(userId = userId, cardIds = listOf(300L, 301L))

                result.keys shouldContainExactlyInAnyOrder listOf(300L, 301L)
                result[300L]!!.cardId shouldBe 300L
                result[300L]!!.fields shouldBe mapOf("front" to "a")
                result[301L]!!.fields shouldBe mapOf("front" to "b")
            }

            it("소유권/존재 조회를 userId로 필터링하므로 타인 소유·미존재 카드는 맵에 포함되지 않는다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front"))
                val deck = newDeck(id = 10L, userId = userId)
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                // 999L은 타인 소유(또는 미존재)라 userId 필터 쿼리 결과에 포함되지 않음
                every {
                    cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(listOf(300L, 999L), userId)
                } returns listOf(card)

                val result = cardService.getCardsByIds(userId = userId, cardIds = listOf(300L, 999L))

                result.keys shouldContainExactlyInAnyOrder listOf(300L)
                result.containsKey(999L) shouldBe false
            }

            it("템플릿 필드가 노트에 없으면 빈 문자열로 채운다") {
                val userId = 1L
                val noteType = newNoteType(id = 1L)
                val tpl = newTemplate(id = 100L, noteType = noteType, requiredFields = listOf("front", "back"))
                val deck = newDeck(id = 10L, userId = userId)
                val note = newNote(id = 200L, userId = userId, fields = mapOf("front" to "a"))
                val card = newCard(id = 300L, template = tpl, note = note, deck = deck, userId = userId)

                every {
                    cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(listOf(300L), userId)
                } returns listOf(card)

                val result = cardService.getCardsByIds(userId = userId, cardIds = listOf(300L))

                result[300L]!!.fields shouldBe mapOf("front" to "a", "back" to "")
            }

            it("빈 입력이면 레포지토리를 호출하지 않고 빈 맵을 반환한다") {
                val result = cardService.getCardsByIds(userId = 1L, cardIds = emptyList())

                result shouldBe emptyMap()
                verify(exactly = 0) {
                    cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(any(), any())
                }
            }
        }
    })
