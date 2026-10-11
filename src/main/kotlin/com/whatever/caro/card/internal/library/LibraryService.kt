package com.whatever.caro.card.internal.library

import com.whatever.caro.card.internal.card.CardService
import com.whatever.caro.card.internal.card.dto.create.CardType
import com.whatever.caro.card.internal.card.dto.create.CreateCardItemDto
import com.whatever.caro.card.internal.card.dto.create.CreateCardsDto
import com.whatever.caro.card.internal.deck.DeckPresetRepository
import com.whatever.caro.card.internal.deck.DeckRepository
import com.whatever.caro.card.internal.deck.dto.create.CreateDeckDto
import com.whatever.caro.card.internal.deck.service.DeckService
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class LibraryService(
    private val decks: LibraryDeckRepository,
    private val cards: LibraryCardRepository,
    private val receipts: LibraryCopyReceiptRepository,
    private val deckService: DeckService,
    private val cardService: CardService,
    private val personalDecks: DeckRepository,
    private val presets: DeckPresetRepository,
) {
    @Transactional(readOnly = true)
    fun list(): List<LibraryDeckResponse> {
        val available = decks.findByPublishedTrueOrderBySortOrderAscIdAsc()
        if (available.isEmpty()) return emptyList()
        val counts = cards.counts(available.map { it.id }).associate { it.libraryDeckId to it.cardCount.toInt() }
        return available.map { LibraryDeckResponse(it.id, it.name, it.description, counts[it.id] ?: 0) }
    }

    @Transactional(readOnly = true)
    fun detail(
        libraryDeckId: Long,
    ): LibraryDetailResponse {
        val deck = decks.findById(libraryDeckId).orElse(null)?.takeIf { it.published }
            ?: throw BusinessException(LibraryErrorCode.UNAVAILABLE)
        val content = cards.findByLibraryDeckIdOrderByPositionAscIdAsc(deck.id)
        return LibraryDetailResponse(
            deck.id,
            deck.name,
            deck.description,
            content.size,
            content.map { LibraryCardResponse(it.id, it.front, it.back, it.position) },
        )
    }

    @Transactional
    fun copy(
        userId: Long,
        libraryDeckId: Long,
        requestKey: UUID,
    ): LibraryCopyResponse {
        val key = requestKey.toString()
        receipts.claim(userId, key, libraryDeckId)
        val receipt = requireNotNull(receipts.lockByKey(userId, key))
        if (receipt.libraryDeckId != libraryDeckId) throw BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT)
        receipt.result()?.let { return it }

        // Lock the source before numbering and reading cards. Publishing tools must use this same lock.
        val source = decks.lockById(libraryDeckId)?.takeIf { it.published }
            ?: throw BusinessException(LibraryErrorCode.UNAVAILABLE)
        val content = cards.findByLibraryDeckIdOrderByPositionAscIdAsc(source.id)
        if (source.name.isBlank() || source.name.length > 50 ||
            source.description.isBlank() || source.description.length > 500 ||
            content.isEmpty() || content.size > 200 || content.any { it.front.isBlank() || it.back.isBlank() }
        ) {
            throw BusinessException(LibraryErrorCode.INVALID_CONTENT)
        }
        val ordinal = receipts.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(userId, source.id) + 1
        val name = personalName(source.name, ordinal)
        val deck = deckService.createDeck(userId, CreateDeckDto(name, source.description))
        // The detail endpoint requires a preset immediately after the copy response.
        personalDecks.findById(deck.id).orElseThrow().deckPreset =
            presets.findById(1L).orElseThrow { BusinessException(LibraryErrorCode.INVALID_CONTENT) }
        val created = cardService.createCards(
            userId,
            CreateCardsDto(
                deck.id,
                content.map {
                    CreateCardItemDto(CardType.BASIC.noteTypeId, mapOf("front" to it.front, "back" to it.back))
                },
            ),
        )
        val result = LibraryCopyResponse(deck.id, name, source.description, created.items.size)
        receipt.complete(ordinal, result)
        // CardService clears the persistence context after updating the card count.
        receipts.save(receipt)
        return result
    }

    internal fun personalName(
        original: String,
        ordinal: Long,
    ): String {
        if (ordinal == 1L) return original
        val suffix = " ($ordinal)"
        val limit = 50 - suffix.length
        val prefix = original.take(limit)
        return prefix.dropLast(if (prefix.lastOrNull()?.isHighSurrogate() == true) 1 else 0) + suffix
    }
}
