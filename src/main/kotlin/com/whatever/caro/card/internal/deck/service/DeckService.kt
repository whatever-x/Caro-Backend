package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.internal.deck.Deck
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponseDto
import com.whatever.caro.card.internal.deck.event.DeckCreatedEvent
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
}
