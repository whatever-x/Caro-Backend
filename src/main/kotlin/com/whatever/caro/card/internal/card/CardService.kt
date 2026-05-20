package com.whatever.caro.card.internal.card

import com.whatever.caro.card.api.event.CardsCreatedEvent
import com.whatever.caro.card.api.event.CardsDeletedEvent
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsResponseDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardDto
import com.whatever.caro.card.internal.card.dto.delete.DeleteCardResponseDto
import com.whatever.caro.card.internal.card.dto.read.CardResponseDto
import com.whatever.caro.card.internal.card.dto.update.UpdateCardDto
import com.whatever.caro.card.internal.card.dto.update.UpdateCardResponseDto
import com.whatever.caro.card.internal.card.exception.CardForbiddenException
import com.whatever.caro.card.internal.card.exception.CardInvalidFieldsException
import com.whatever.caro.card.internal.card.exception.CardNotFoundException
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.note.Note
import com.whatever.caro.card.internal.note.NoteRepository
import com.whatever.caro.card.internal.notetype.CardTemplate
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNoTemplatesException
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createCards(
        userId: Long,
        dto: CreateCardsDto,
    ): CreateCardsResponseDto {
        val deck = deckRepository.findByIdAndDeletedAtIsNull(dto.deckId)
            ?: throw DeckNotFoundException("deckId=${dto.deckId} 덱을 찾을 수 없습니다")

        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=${dto.deckId} 에 대한 접근 권한이 없습니다")
        }

        // 여러개중에 하나라도 터지면, 실패시키는게 맞을까 ???
        val createdCards = dto.items.flatMap { item ->
            noteTypeRepository.findById(item.noteTypeId).orElseThrow {
                NoteTypeNotFoundException("noteTypeId=${item.noteTypeId} 노트 타입을 찾을 수 없습니다")
            }

            val cardTemplates = cardTemplateRepository.findByNoteTypeId(item.noteTypeId)
            if (cardTemplates.isEmpty()) {
                throw NoteTypeNoTemplatesException("noteTypeId=${item.noteTypeId} 에 카드 템플릿이 없습니다")
            }

            cardTemplates.forEach { template ->
                val missing = template.requiredFields - item.fields.keys
                if (missing.isNotEmpty()) {
                    throw CardInvalidFieldsException("필수 필드가 누락되었습니다: $missing")
                }
            }

            val note = noteRepository.save(Note(userId = userId, fields = item.fields))

            cardTemplates.map { template ->
                val card = cardRepository.save(
                    Card(
                        cardTemplate = template,
                        note = note,
                        deck = deck,
                        userId = userId,
                    ),
                )
                CardResponseDto(
                    cardId = card.id,
                    fields = projectFields(template, note.fields),
                )
            }
        }

        deck.cardCount += createdCards.size
        eventPublisher.publishEvent(
            CardsCreatedEvent(cardIds = createdCards.map { it.cardId }, userId = userId),
        )

        return CreateCardsResponseDto(items = createdCards)
    }

    @Transactional(readOnly = true)
    fun getCard(
        userId: Long,
        cardId: Long,
    ): CardResponseDto {
        val card = cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(cardId)
            ?: throw CardNotFoundException("cardId=$cardId 카드를 찾을 수 없습니다")

        if (card.userId != userId) {
            throw CardForbiddenException("cardId=$cardId 에 대한 접근 권한이 없습니다")
        }

        return CardResponseDto(
            cardId = card.id,
            fields = projectFields(card.cardTemplate, card.note.fields),
        )
    }

    @Transactional(readOnly = true)
    fun getCardsByDeck(
        userId: Long,
        deckId: Long,
    ): List<CardResponseDto> {
        val deck = deckRepository.findByIdAndDeletedAtIsNull(deckId)
            ?: throw DeckNotFoundException("deckId=$deckId 덱을 찾을 수 없습니다")

        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=$deckId 에 대한 접근 권한이 없습니다")
        }

        return cardRepository.findAllByDeckIdAndDeletedAtIsNullWithNoteAndTemplate(deckId)
            .map { card ->
                CardResponseDto(
                    cardId = card.id,
                    fields = projectFields(card.cardTemplate, card.note.fields),
                )
            }
    }

    @Transactional
    fun updateCard(
        userId: Long,
        dto: UpdateCardDto,
    ): UpdateCardResponseDto {
        val card = cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(dto.cardId)
            ?: throw CardNotFoundException("cardId=${dto.cardId} 카드를 찾을 수 없습니다")

        if (card.userId != userId) {
            throw CardForbiddenException("cardId=${dto.cardId} 에 대한 접근 권한이 없습니다")
        }

        val allowedKeys = card.cardTemplate.requiredFields.toSet()
        val invalid = dto.fields.keys - allowedKeys
        if (invalid.isNotEmpty()) {
            throw CardInvalidFieldsException("카드 템플릿에 없는 필드입니다: $invalid")
        }

        card.note.fields = card.note.fields + dto.fields

        return UpdateCardResponseDto(
            cardId = card.id,
            fields = projectFields(card.cardTemplate, card.note.fields),
        )
    }

    @Transactional
    fun deleteCard(
        userId: Long,
        dto: DeleteCardDto,
    ): DeleteCardResponseDto {
        val card = cardRepository.findByIdAndDeletedAtIsNullWithNoteAndTemplate(dto.cardId)
            ?: throw CardNotFoundException("cardId=${dto.cardId} 카드를 찾을 수 없습니다")

        if (card.userId != userId) {
            throw CardForbiddenException("cardId=${dto.cardId} 에 대한 접근 권한이 없습니다")
        }

        val now = Instant.now()
        card.softDelete(deletedAt = now)
        card.deck.cardCount = maxOf(0, card.deck.cardCount - 1)

        if (cardRepository.countByNoteIdAndDeletedAtIsNull(card.note.id) == 0L) {
            card.note.softDelete(deletedAt = now)
        }

        eventPublisher.publishEvent(
            CardsDeletedEvent(deckId = card.deck.id, deletedCount = 1, userId = userId),
        )

        return DeleteCardResponseDto(cardId = card.id)
    }

    private fun projectFields(
        template: CardTemplate,
        noteFields: Map<String, String>,
    ): Map<String, String> =
        template.requiredFields.associateWith { key -> noteFields[key].orEmpty() }
}
