package com.whatever.caro.card.internal.deck

import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckResponseDto
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeckService(
    private val deckRepository: DeckRepository,
) : DeckApi {
    override fun getDecks(userId: Long): List<Deck> {
        return deckRepository.findByUserId(userId) ?: error("에러처리")
    }

    @Transactional
    fun createDeck(userId: Long, createDeckDto: CreateDeckDto): CreateDeckResponseDto {
        val deck = deckRepository.save(
            Deck(
                userId = userId,
                name = createDeckDto.name,
                description = createDeckDto.description
            )
        )
        return CreateDeckResponseDto(
            id = deck.id,
            name = deck.name,
            description = deck.description,
        )
    }
}
