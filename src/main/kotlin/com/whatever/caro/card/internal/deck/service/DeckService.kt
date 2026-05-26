package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.api.deck.DeckDeletedEvent
import com.whatever.caro.card.api.deck.DeckInfoResponse
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponseDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckResponseDto
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckDto
import com.whatever.caro.card.internal.deck.dto.update.UpdateDeckResponseDto
import com.whatever.caro.card.internal.deck.event.created.DeckCreatedEvent
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.deck.toInfo
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class DeckService(
    private val deckRepository: DeckRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : DeckApi {
    override fun getDecks(
        userId: Long,
    ): List<DeckInfoResponse> = deckRepository.findByUserIdAndDeletedAtIsNull(userId).map { it.toInfo() }

    override fun getDeck(
        deckId: Long,
    ): DeckInfoResponse =
        (
            deckRepository.findByIdAndDeletedAtIsNull(deckId)
                ?: throw DeckNotFoundException("deckId=$deckId 덱을 찾을 수 없습니다")
            ).toInfo()

    @Transactional
    fun createDeck(
        userId: Long,
        createDeckDto: CreateDeckDto,
    ): CreateDeckResponseDto {
        val deck = deckRepository.save(
            Deck(
                userId = userId,
                name = createDeckDto.name,
                description = createDeckDto.description,
            ),
        )
        eventPublisher.publishEvent(DeckCreatedEvent(deckId = deck.id, userId = userId))
        return CreateDeckResponseDto(
            id = deck.id,
            name = deck.name,
            description = deck.description,
        )
    }

    @Transactional
    fun deleteDeck(
        userId: Long,
        deleteDeckDto: DeleteDeckDto,
    ): DeleteDeckResponseDto {
        val deck = deckRepository.findById(deleteDeckDto.deckId).orElseThrow {
            DeckNotFoundException("deckId=${deleteDeckDto.deckId} 덱을 찾을 수 없습니다")
        }
        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=${deleteDeckDto.deckId} 에 대한 접근 권한이 없습니다")
        }
        deck.softDelete(deletedAt = Instant.now())
        eventPublisher.publishEvent(DeckDeletedEvent(deckId = deleteDeckDto.deckId, userId = userId))
        return DeleteDeckResponseDto(id = deleteDeckDto.deckId)
    }

    @Transactional
    fun updateDeck(
        userId: Long,
        updateDeckDto: UpdateDeckDto,
    ): UpdateDeckResponseDto {
        val deck = deckRepository.findById(updateDeckDto.deckId).orElseThrow {
            DeckNotFoundException("deckId=${updateDeckDto.deckId} 덱을 찾을 수 없습니다")
        }
        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=${updateDeckDto.deckId} 에 대한 접근 권한이 없습니다")
        }
        deck.name = updateDeckDto.name
        deck.description = updateDeckDto.description
        return UpdateDeckResponseDto(
            id = deck.id,
            name = deck.name,
            description = deck.description,
        )
    }
}
