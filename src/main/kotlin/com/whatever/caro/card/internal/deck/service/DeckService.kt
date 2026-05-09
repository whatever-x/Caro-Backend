package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponseDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckDto
import com.whatever.caro.card.internal.deck.dto.delete.DeleteDeckResponseDto
import com.whatever.caro.card.internal.deck.event.created.DeckCreatedEvent
import com.whatever.caro.card.internal.deck.event.deleted.DeckDeletedEvent
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeckService(
    private val deckRepository: DeckRepository,
    private val eventPublisher: ApplicationEventPublisher,
) : DeckApi {
    override fun getDecks(
        userId: Long,
    ): List<Deck> = deckRepository.findByUserId(userId)

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
        eventPublisher.publishEvent(DeckDeletedEvent(deckId = deleteDeckDto.deckId, userId = userId))
        return DeleteDeckResponseDto(id = deleteDeckDto.deckId)
    }
}
