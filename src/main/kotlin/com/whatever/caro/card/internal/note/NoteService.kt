package com.whatever.caro.card.internal.note

import com.whatever.caro.card.internal.card.Card
import com.whatever.caro.card.internal.card.CardRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.note.dto.create.CreateNoteDto
import com.whatever.caro.card.internal.note.dto.create.CreateNoteResponseDto
import com.whatever.caro.card.internal.note.dto.read.NoteWithCardsResponseDto
import com.whatever.caro.card.api.note.CardsCreatedEvent
import com.whatever.caro.card.internal.note.dto.delete.DeleteNoteDto
import com.whatever.caro.card.internal.note.dto.delete.DeleteNoteResponseDto
import com.whatever.caro.card.internal.note.dto.update.UpdateNoteDto
import com.whatever.caro.card.internal.note.dto.update.UpdateNoteResponseDto
import com.whatever.caro.card.internal.note.exception.NoteForbiddenException
import com.whatever.caro.card.internal.note.exception.NoteInvalidFieldsException
import com.whatever.caro.card.internal.note.exception.NoteNotFoundException
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNotFoundException
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNoTemplatesException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
    private val eventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun createNote(
        userId: Long,
        dto: CreateNoteDto,
    ): CreateNoteResponseDto {
        val deck = deckRepository.findByIdAndDeletedAtIsNull(dto.deckId)
            ?: throw DeckNotFoundException("deckId=${dto.deckId} 덱을 찾을 수 없습니다")

        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=${dto.deckId} 에 대한 접근 권한이 없습니다")
        }

        noteTypeRepository.findById(dto.noteTypeId).orElseThrow {
            NoteTypeNotFoundException("noteTypeId=${dto.noteTypeId} 노트 타입을 찾을 수 없습니다")
        }

        val cardTemplates = cardTemplateRepository.findByNoteTypeId(dto.noteTypeId)
        if (cardTemplates.isEmpty()) {
            throw NoteTypeNoTemplatesException("noteTypeId=${dto.noteTypeId} 에 카드 템플릿이 없습니다")
        }

        // 모든 템플릿의 required_fields 를 충족하는지 런타임 검증
        cardTemplates.forEach { template ->
            val missing = template.requiredFields - dto.fields.keys
            if (missing.isNotEmpty()) {
                throw NoteInvalidFieldsException("필수 필드가 누락되었습니다: $missing")
            }
        }

        val note = noteRepository.save(Note(userId = userId, fields = dto.fields))

        val cards = cardTemplates.map { template ->
            cardRepository.save(
                Card(
                    cardTemplate = template,
                    note = note,
                    deck = deck,
                    userId = userId,
                ),
            )
        }

        deck.cardCount += cards.size
        eventPublisher.publishEvent(CardsCreatedEvent(cardIds = cards.map { it.id }, userId = userId))

        return CreateNoteResponseDto(
            noteId = note.id,
            fields = note.fields,
            cardIds = cards.map { it.id },
        )
    }

    @Transactional(readOnly = true)
    fun getNotesByDeck(
        userId: Long,
        deckId: Long,
    ): List<NoteWithCardsResponseDto> {
        val deck = deckRepository.findByIdAndDeletedAtIsNull(deckId)
            ?: throw DeckNotFoundException("deckId=$deckId 덱을 찾을 수 없습니다")

        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=$deckId 에 대한 접근 권한이 없습니다")
        }

        val cards = cardRepository.findByDeckIdAndDeletedAtIsNull(deckId)

        return cards
            .groupBy { it.note.id }
            .map { (noteId, noteCards) ->
                NoteWithCardsResponseDto(
                    noteId = noteId,
                    fields = noteCards.first().note.fields,
                    cardIds = noteCards.map { it.id },
                )
            }
    }

    @Transactional
    fun updateNote(
        userId: Long,
        dto: UpdateNoteDto,
    ): UpdateNoteResponseDto {
        val note = noteRepository.findByIdAndDeletedAtIsNull(dto.noteId)
            ?: throw NoteNotFoundException("noteId=${dto.noteId} 노트를 찾을 수 없습니다")

        if (note.userId != userId) {
            throw NoteForbiddenException("noteId=${dto.noteId} 에 대한 접근 권한이 없습니다")
        }

        // 기존 카드들의 requiredFields 검증 (템플릿 구조 유지)
        cardRepository.findByNoteIdAndDeletedAtIsNull(dto.noteId).forEach { card ->
            val missing = card.cardTemplate.requiredFields - dto.fields.keys
            if (missing.isNotEmpty()) {
                throw NoteInvalidFieldsException("필수 필드가 누락되었습니다: $missing")
            }
        }

        note.fields = dto.fields

        return UpdateNoteResponseDto(
            noteId = note.id,
            fields = note.fields,
        )
    }

    @Transactional
    fun deleteNote(
        userId: Long,
        dto: DeleteNoteDto,
    ): DeleteNoteResponseDto {
        val note = noteRepository.findByIdAndDeletedAtIsNull(dto.noteId)
            ?: throw NoteNotFoundException("noteId=${dto.noteId} 노트를 찾을 수 없습니다")

        if (note.userId != userId) {
            throw NoteForbiddenException("noteId=${dto.noteId} 에 대한 접근 권한이 없습니다")
        }

        val now = Instant.now()
        note.softDelete(deletedAt = now)
        cardRepository.findByNoteIdAndDeletedAtIsNull(dto.noteId).forEach { it.softDelete(deletedAt = now) }

        return DeleteNoteResponseDto(noteId = dto.noteId)
    }
}
