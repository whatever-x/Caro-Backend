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
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.PessimisticLockingFailureException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

@Service
class LibraryService(
    private val libraryDeckRepository: LibraryDeckRepository,
    private val libraryCardRepository: LibraryCardRepository,
    private val copyReceiptRepository: LibraryCopyReceiptRepository,
    private val deckService: DeckService,
    private val cardService: CardService,
    private val deckRepository: DeckRepository,
    private val deckPresetRepository: DeckPresetRepository,
    transactionManager: PlatformTransactionManager,
) {
    // A failed attempt must finish rolling back before a new transaction and snapshot are used.
    private val copyTransaction = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
    }

    @Transactional(readOnly = true)
    fun list(): List<LibraryDeckResponse> {
        val availableDecks = libraryDeckRepository.findByPublishedTrueOrderBySortOrderAscIdAsc()
        if (availableDecks.isEmpty()) {
            return emptyList()
        }

        val deckIds = availableDecks.map { it.id }
        val cardCounts = libraryCardRepository.counts(deckIds)
            .associate { count -> count.libraryDeckId to count.cardCount.toInt() }

        return availableDecks.map { deck ->
            LibraryDeckResponse(
                libraryDeckId = deck.id,
                name = deck.name,
                description = deck.description,
                cardCount = cardCounts[deck.id] ?: 0,
            )
        }
    }

    @Transactional(readOnly = true)
    fun detail(
        libraryDeckId: Long,
    ): LibraryDetailResponse {
        val deck = libraryDeckRepository.findById(libraryDeckId)
            .orElseThrow { BusinessException(LibraryErrorCode.UNAVAILABLE) }
        if (!deck.published) {
            throw BusinessException(LibraryErrorCode.UNAVAILABLE)
        }

        val sourceCards = libraryCardRepository.findByLibraryDeckIdOrderByPositionAscIdAsc(deck.id)
        val cardResponses = sourceCards.map { card ->
            LibraryCardResponse(
                libraryCardId = card.id,
                front = card.front,
                back = card.back,
                position = card.position,
            )
        }

        return LibraryDetailResponse(
            libraryDeckId = deck.id,
            name = deck.name,
            description = deck.description,
            cardCount = sourceCards.size,
            cards = cardResponses,
        )
    }

    fun copy(
        userId: Long,
        libraryDeckId: Long,
        requestKey: UUID,
    ): LibraryCopyResponse {
        var failures = 0
        while (true) {
            try {
                return requireNotNull(copyTransaction.execute { copyInTransaction(userId, libraryDeckId, requestKey) })
            } catch (exception: RuntimeException) {
                if (++failures >= MAX_COPY_ATTEMPTS || !isRetryableCopyFailure(exception)) {
                    throw exception
                }
            }
        }
    }

    private fun isRetryableCopyFailure(
        exception: RuntimeException,
    ): Boolean {
        if (exception is PessimisticLockingFailureException) {
            return true
        }
        return exception is DataIntegrityViolationException &&
            generateSequence<Throwable>(exception) { it.cause }
                .filterIsInstance<ConstraintViolationException>()
                .any { it.constraintName?.substringAfterLast('.') == "uk_library_copy_ordinal" }
    }

    private fun copyInTransaction(
        userId: Long,
        libraryDeckId: Long,
        requestKey: UUID,
    ): LibraryCopyResponse {
        val key = requestKey.toString()
        copyReceiptRepository.claim(userId, key, libraryDeckId)
        val receipt = requireNotNull(copyReceiptRepository.lockByKey(userId, key))
        if (receipt.libraryDeckId != libraryDeckId) {
            throw BusinessException(CommonErrorCode.IDEMPOTENCY_KEY_CONFLICT)
        }
        if (receipt.isCompleted()) {
            return receipt.toCopyResponse()
        }

        // Publishers must take an exclusive source lock; concurrent copies share this read lock.
        val source = libraryDeckRepository.lockForCopy(libraryDeckId)
            ?: throw BusinessException(LibraryErrorCode.UNAVAILABLE)
        if (!source.published) {
            throw BusinessException(LibraryErrorCode.UNAVAILABLE)
        }

        val sourceCards = libraryCardRepository.findByLibraryDeckIdOrderByPositionAscIdAsc(source.id)
        if (!source.isValidForCopy(sourceCards)) {
            throw BusinessException(LibraryErrorCode.INVALID_CONTENT)
        }

        val ordinal = copyReceiptRepository.countByUserIdAndLibraryDeckIdAndDeckIdIsNotNull(userId, source.id) + 1
        val deckName = personalName(source.name, ordinal)
        val createdDeck = deckService.createDeck(
            userId = userId,
            createDeckDto = CreateDeckDto(name = deckName, description = source.description),
        )

        // The detail endpoint requires a preset immediately after the copy response.
        val personalDeck = deckRepository.findById(createdDeck.id).orElseThrow()
        val defaultPreset = deckPresetRepository.findById(1L)
            .orElseThrow { BusinessException(LibraryErrorCode.INVALID_CONTENT) }
        personalDeck.deckPreset = defaultPreset

        val cardItems = sourceCards.map { card ->
            CreateCardItemDto(
                noteTypeId = CardType.BASIC.noteTypeId,
                fields = mapOf("front" to card.front, "back" to card.back),
            )
        }
        val createdCards = cardService.createCards(
            userId = userId,
            dto = CreateCardsDto(deckId = createdDeck.id, items = cardItems),
        )

        receipt.complete(number = ordinal, deck = personalDeck, cardCount = createdCards.items.size)
        return receipt.toCopyResponse()
    }

    companion object {
        private const val MAX_COPY_ATTEMPTS = 3
    }

    internal fun personalName(
        original: String,
        ordinal: Long,
    ): String {
        if (ordinal == 1L) {
            return original
        }

        val suffix = " ($ordinal)"
        val maxBaseNameLength = 50 - suffix.length
        val baseName = original.take(maxBaseNameLength)
        val truncatedName = if (baseName.lastOrNull()?.isHighSurrogate() == true) {
            baseName.dropLast(1)
        } else {
            baseName
        }
        return truncatedName + suffix
    }
}
