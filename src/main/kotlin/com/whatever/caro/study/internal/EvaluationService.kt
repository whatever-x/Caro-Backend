package com.whatever.caro.study.internal

import com.whatever.caro.card.DeckPresetApi
import com.whatever.caro.card.DeckPresetDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.ReviewLog
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class EvaluationService(
    private val studySessionRepository: StudySessionRepository,
    private val deckPresetApi: DeckPresetApi,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
) {
    @Transactional
    fun evaluate(
        now: Instant,
        userId: Long,
        sessionId: Long,
        items: List<EvaluatedCardDto>,
    ): EvaluationResult {
        val session = studySessionRepository.findByIdAndUserId(
            id = sessionId,
            userId = userId,
        ) ?: throw RuntimeException() // TODO custom exception
        if (session.status != StudySessionStatus.ACTIVE) {
            throw RuntimeException() // TODO custom exception
        }
        if (session.isTodaySession(now).not()) {
            throw SessionExpiredException()
        }

        val params = deckPresetApi.getDeckPresetById(session.deckPresetIdSnapshot).toSm2Params()

        // 평가 아이템 검증
        val dedupedItems = items.asReversed().distinctBy { it.cardId }.asReversed()
        val clsByCardId = cardLearningStateRepository.findAllByUserIdAndCardIdIn(
            userId = userId,
            cardIds = dedupedItems.map { it.cardId },
        ).associateBy { it.cardId }

        val evaluatedCardIds = reviewLogRepository.findAllByStudySessionId(session.id).map { it.cardId }.toSet()
        val (validItems, invalidItems) = dedupedItems.map {
            EvaluationItemValidator.validate(
                item = it,
                evaluatedCardIds = evaluatedCardIds,
            )
        }.partition { it is ValidatedItem }

        // 평가 계산 & 기록
        val newReviewLogs = validItems.filterIsInstance<ValidatedItem>().map {
            val cls = clsByCardId[it.item.cardId] ?: throw RuntimeException() // TODO custom exception

            val context = SchedulingContext(now, params)
            val nextState = cls.toSchedulingState().nextStates(context).pick(it.item.rating)

            val reviewLog = ReviewLog(
                studySession = session,
                cardId = cls.cardId,
                userId = cls.userId,
                rating = it.item.rating,
                timeMs = it.item.timeMs,
                reviewType = if (cls.status == CardLearningStatus.NEW) ReviewType.NEW else ReviewType.REVIEW,
                previousIntervalDays = cls.intervalDays,
                previousEaseFactor = cls.easeFactor,
                previousCardStatus = cls.status,
                intervalDays = nextState.intervalDays,
                easeFactor = nextState.easeFactor,
            )

            cls.applyScheduling(
                now = now,
                nextState = nextState,
            )

            session.updateStudiedCard(reviewLog.reviewType)
            reviewLog
        }
        if (session.newCardsStudied + session.reviewCardsStudied >= session.estimatedTotal) {
            session.complete(now)
        }

        reviewLogRepository.saveAll(newReviewLogs)

        return EvaluationResult(
            evaluatedItems = validItems,
            failedItems = invalidItems,
            sessionStatus = session.status,
        )
    }
}

object EvaluationItemValidator {
    fun validate(
        item: EvaluatedCardDto,
        evaluatedCardIds: Set<Long>,
    ): ValidationResult =
        when {
            isValidTimeMs(item).not() -> InvalidItem(item)
            isAlreadyEvaluated(item, evaluatedCardIds) -> InvalidItem(item)
            else -> ValidatedItem(item)
        }

    private fun isValidTimeMs(
        item: EvaluatedCardDto,
    ): Boolean = item.timeMs in 0..600000

    private fun isAlreadyEvaluated(
        item: EvaluatedCardDto,
        evaluatedCardIds: Set<Long>,
    ): Boolean = item.cardId in evaluatedCardIds
}

sealed interface ValidationResult
data class ValidatedItem(
    val item: EvaluatedCardDto,
) : ValidationResult
data class InvalidItem(
    val item: EvaluatedCardDto,
) : ValidationResult

private fun DeckPresetDto.toSm2Params(): Sm2Params =
    Sm2Params(
        newFairInterval = newFairInterval,
        newEasyInterval = newEasyInterval,
        newInitialEaseFactor = newInitialEaseFactor,
        reviewMaxInterval = reviewMaxInterval,
        lapseIntervalMultiplier = lapseIntervalMultiplier,
        lapseMinInterval = lapseMinInterval,
        leechThreshold = leechThreshold,
    )
