package com.whatever.caro.card.internal.deck.service

import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.card.api.deck.DeckPresetDto
import com.whatever.caro.card.internal.deck.DeckPreset
import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.exception.DeckForbiddenException
import com.whatever.caro.card.internal.deck.exception.DeckNotFoundException
import com.whatever.caro.card.internal.deck.exception.DeckPresetNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeckPresetService(
    private val deckRepository: DeckRepository,
    private val deckPresetRepository: DeckPresetRepository,
) : DeckPresetApi {

    @Transactional(readOnly = true)
    override fun getLatestDeckPresetsByDeckId(
        userId: Long,
        deckIds: Collection<Long>,
    ): Map<Long, DeckPresetDto> =
        deckRepository.findAllByIdInWithPreset(
            userId = userId,
            deckIds = deckIds,
        ).mapNotNull { deck ->
            deck.deckPreset?.let { deck.id to it.toDto() }
        }.toMap()

    @Transactional(readOnly = true)
    override fun getLatestDeckPresetByUser(
        deckId: Long,
        userId: Long,
    ): DeckPresetDto {
        val deck = deckRepository.findByIdWithPreset(deckId)
            ?: throw DeckNotFoundException("deckId=$deckId 덱을 찾을 수 없습니다")

        if (deck.userId != userId) {
            throw DeckForbiddenException("deckId=$deckId 에 대한 접근 권한이 없습니다")
        }

        val preset = deck.deckPreset
            ?: throw DeckPresetNotFoundException("deckId=$deckId 에 할당된 덱 프리셋이 없습니다")
        return preset.toDto()
    }

    @Transactional(readOnly = true)
    override fun getDeckPresetById(
        deckPresetIdSnapshot: Long,
    ): DeckPresetDto {
        val preset = deckPresetRepository.findByIdOrNull(deckPresetIdSnapshot)
            ?: throw DeckPresetNotFoundException("deckPresetId=$deckPresetIdSnapshot 덱 프리셋을 찾을 수 없습니다")

        return preset.toDto()
    }
}

private fun DeckPreset.toDto(): DeckPresetDto =
    DeckPresetDto(
        id = id,
        userId = userId,
        name = name,
        newPerDay = newPerDay,
        newFairInterval = newFairInterval,
        newEasyInterval = newEasyInterval,
        newInitialEaseFactor = newInitialEaseFactor,
        reviewPerDay = reviewPerDay,
        reviewMaxInterval = reviewMaxInterval,
        lapseIntervalMultiplier = lapseIntervalMultiplier,
        lapseMinInterval = lapseMinInterval,
        leechThreshold = leechThreshold,
        hardBadgeThreshold = hardBadgeThreshold,
    )
