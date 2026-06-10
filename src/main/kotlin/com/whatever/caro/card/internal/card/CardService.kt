package com.whatever.caro.card.internal.card

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.card.CardContentDto
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
import java.time.Clock
import java.time.Instant

@Service
class CardService(
    private val cardRepository: CardRepository,
    private val noteRepository: NoteRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) : CardApi {
    override fun getCardsByIds(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardContentDto> {
        if (cardIds.isEmpty()) {
            return emptyMap()
        }
        return cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(cardIds, userId)
            .associate { card ->
                card.id to CardContentDto(
                    cardId = card.id,
                    fields = projectFields(card.cardTemplate, card.note.fields),
                )
            }
    }

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
        val noteTypeIds = dto.items.map { it.noteTypeId }.toSet()
        val foundNoteTypeIds = noteTypeRepository.findAllByIdIn(noteTypeIds).map { it.id }.toSet()
        val missingNoteTypeIds = noteTypeIds - foundNoteTypeIds
        if (missingNoteTypeIds.isNotEmpty()) {
            throw NoteTypeNotFoundException("noteTypeId=$missingNoteTypeIds 노트 타입을 찾을 수 없습니다")
        }

        val templatesByNoteType = cardTemplateRepository.findAllByNoteTypeIdIn(foundNoteTypeIds)
            .groupBy { it.noteType.id }

        dto.items.forEach { item ->
            val cardTemplates = templatesByNoteType[item.noteTypeId]
                ?: throw NoteTypeNoTemplatesException("noteTypeId=${item.noteTypeId} 에 카드 템플릿이 없습니다")
            cardTemplates.forEach { template ->
                val missing = template.requiredFields - item.fields.keys
                if (missing.isNotEmpty()) {
                    throw CardInvalidFieldsException("필수 필드가 누락되었습니다: $missing")
                }
            }
        }

        val notes = noteRepository.saveAll(
            dto.items.map { Note(userId = userId, fields = it.fields) },
        )

        val cardsToSave = dto.items.zip(notes).flatMap { (item, note) ->
            templatesByNoteType.getValue(item.noteTypeId).map { template ->
                Card(
                    cardTemplate = template,
                    note = note,
                    deck = deck,
                    userId = userId,
                )
            }
        }

        val createdCards = cardRepository.saveAll(cardsToSave).map { card ->
            CardResponseDto(
                cardId = card.id,
                fields = projectFields(card.cardTemplate, card.note.fields),
            )
        }

        deck.cardCount += createdCards.size
        eventPublisher.publishEvent(
            CardsCreatedEvent(cardIds = createdCards.map { it.cardId }, deckId = deck.id, userId = userId),
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

    @Transactional(readOnly = true)
    override fun getCardContentsByDeck(
        userId: Long,
        deckId: Long,
    ): List<CardContentDto> {
        val cardResponseDtos = getCardsByDeck(userId, deckId)
        return cardResponseDtos.map {
            CardContentDto(
                cardId = it.cardId,
                fields = it.fields,
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

        val now = Instant.now(clock)
        card.softDelete(deletedAt = now)
        card.deck.cardCount = maxOf(0, card.deck.cardCount - 1)

        if (cardRepository.countByNoteIdAndDeletedAtIsNullAndIdNot(card.note.id, card.id) == 0L) {
            card.note.softDelete(deletedAt = now)
        }

        eventPublisher.publishEvent(
            CardsDeletedEvent(
                deckId = card.deck.id,
                deletedCount = 1,
                userId = userId,
                deletedCardIds = setOf(card.id),
                deletedAt = now,
            ),
        )

        return DeleteCardResponseDto(cardId = card.id)
    }

    private fun projectFields(
        template: CardTemplate,
        noteFields: Map<String, String>,
    ): Map<String, String> = template.requiredFields.associateWith { key -> noteFields[key].orEmpty() }
}
