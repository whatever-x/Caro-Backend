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
        // 받은 카드들 중에서 내게 아닌 카드가 섞여있는지 체크. 한개라도 있다면 카드 삭제 수행 하지 않음
        val cards = cardRepository.findAllByIds(dto.cardIds)
        val anotherUserCard = cards.find { it.userId != userId }
        if (anotherUserCard != null) throw CardForbiddenException("cardId=${anotherUserCard.id} 에 대한 접근 권한이 없습니다")

        val (aliveCards, _) = cards.partition { it.deletedAt == null }

        // 삭제 되지 않은 카드들에 대해서만 조회해서 삭제처리
        // 0 개라면 이벤트, 삭제처리 없이 그대로 종료
        val deletedCards = aliveCards
            .map { aliveCard -> aliveCard.id }
            .takeIf { it.isNotEmpty() }
            ?.let { cards ->
                cardRepository.findAllByIdInAndUserIdAndDeletedAtIsNullWithNoteAndTemplate(
                    ids = cards,
                    userId = userId,
                )
            }
            ?: return DeleteCardResponseDto(deletedCardsCount = 0)

        val referencedNoteIds = deletedCards.map { it.note.id }.toSet()
        val survivorNoteIds = cardRepository.findSurvivorNoteIdsByNoteIdInExcludingCards(
            referencedNoteIds = referencedNoteIds,
            deletingCardIds = deletedCards.map { it.id },
        ).toSet()
        val orphanNoteIds = referencedNoteIds - survivorNoteIds

        val now = Instant.now(clock)
        // 삭제 및 연관관계인 덱쪽의 카드카운트를 1개 빼줌
        deletedCards.forEach { card ->
            card.softDelete(now)
            card.deck.cardCount = maxOf(0, card.deck.cardCount - 1)
        }
        // 노트가 더이상 참조하는 카드가 없다면 노트도 삭제
        deletedCards.filter { it.note.id in orphanNoteIds }
            .forEach { card -> card.note.softDelete(now) }

        // TODO 배치삭제로 수정 필요
        // 덱 별로 삭제 이벤트 발행
        deletedCards.groupBy { it.deck.id }
            .forEach { (deckId, deckCards) ->
                eventPublisher.publishEvent(
                    CardsDeletedEvent(
                        deckId = deckId,
                        deletedCount = deckCards.size,
                        userId = userId,
                        deletedCardIds = deckCards.map { it.id }.toSet(),
                        deletedAt = now,
                    ),
                )
            }

        return DeleteCardResponseDto(deletedCardsCount = cards.size)
    }

    private fun projectFields(
        template: CardTemplate,
        noteFields: Map<String, String>,
    ): Map<String, String> = template.requiredFields.associateWith { key -> noteFields[key].orEmpty() }
}
