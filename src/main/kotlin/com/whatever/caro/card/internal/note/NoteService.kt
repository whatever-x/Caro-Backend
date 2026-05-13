package com.whatever.caro.card.internal.note

import com.whatever.caro.card.internal.card.Card
import com.whatever.caro.card.internal.card.CardRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.note.dto.create.CreateNoteDto
import com.whatever.caro.card.internal.note.dto.create.CreateNoteResponseDto
import com.whatever.caro.card.internal.note.exception.NoteInvalidFieldsException
import com.whatever.caro.card.internal.notetype.CardTemplateRepository
import com.whatever.caro.card.internal.notetype.NoteTypeRepository
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNotFoundException
import com.whatever.caro.card.internal.notetype.exception.NoteTypeNoTemplatesException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NoteService(
    private val noteRepository: NoteRepository,
    private val cardRepository: CardRepository,
    private val deckRepository: DeckRepository,
    private val noteTypeRepository: NoteTypeRepository,
    private val cardTemplateRepository: CardTemplateRepository,
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

        return CreateNoteResponseDto(
            noteId = note.id,
            fields = note.fields,
            cardIds = cards.map { it.id },
        )
    }
}
