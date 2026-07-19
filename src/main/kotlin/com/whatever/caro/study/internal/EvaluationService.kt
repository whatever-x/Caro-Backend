package com.whatever.caro.study.internal

import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.card.api.deck.DeckPresetDto
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.DailyStudyCompletedEvent
import com.whatever.caro.study.Rating
import com.whatever.caro.study.ReviewType
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.exception.SessionNotActiveException
import com.whatever.caro.study.exception.SessionNotFoundException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.ReviewLog
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

@Service
class EvaluationService(
    private val studySessionRepository: StudySessionRepository,
    private val deckPresetApi: DeckPresetApi,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
) {
    @Transactional
    fun evaluate(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        sessionId: Long,
        items: List<EvaluatedCardDto>,
    ): EvaluationResult {
        val session = studySessionRepository.findByIdAndUserId(
            id = sessionId,
            userId = userId,
        ) ?: throw SessionNotFoundException()
        if (session.status != StudySessionStatus.ACTIVE) {
            throw SessionNotActiveException()
        }
        if (session.isTodaySession(now, timezone).not()) {
            throw SessionExpiredException()
        }

        val params = deckPresetApi.getDeckPresetById(session.deckPresetIdSnapshot).toSm2Params()

        // 평가 아이템 검증
        val dedupedItems = items.asReversed().distinctBy { it.cardId }.asReversed()
        val clsByCardId = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(
            userId = userId,
            cardIds = dedupedItems.map { it.cardId },
        ).associateBy { it.cardId }

        val existingReviewLogs = reviewLogRepository.findAllByStudySessionId(session.id)
        val evaluatedCardIds = existingReviewLogs.map { it.cardId }.toSet()
        val validationResults = dedupedItems.map {
            EvaluationItemValidator.validate(
                item = it,
                evaluatedCardIds = evaluatedCardIds,
            )
        }
        val validItems = validationResults.filterIsInstance<ValidItem>()
        val invalidItems = validationResults.filterIsInstance<InvalidItem>()

        // 평가 계산 & 기록
        val newReviewLogs = validItems.map {
            val cls = clsByCardId[it.item.cardId]
                ?: error("CardLearningState not exist for cardId=${it.item.cardId}")

            val context = SchedulingContext(studyDate = session.sessionDate, params = params)
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
                studyDate = session.sessionDate,
                nextState = nextState,
            )

            session.updateStudiedCard(reviewLog.reviewType)
            reviewLog
        }
        if (session.completeIfGoalAchieved(now)) {
            applicationEventPublisher.publishEvent(
                DailyStudyCompletedEvent(
                    userId = userId,
                    studyDate = session.sessionDate,
                ),
            )
        }

        reviewLogRepository.saveAll(newReviewLogs)

        return EvaluationResult(
            evaluatedItems = validItems,
            failedItems = invalidItems,
            sessionStatus = session.status,
            ratingCounts = (existingReviewLogs + newReviewLogs).toRatingCounts(),
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
            else -> ValidItem(item)
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
data class ValidItem(
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

private fun List<ReviewLog>.toRatingCounts(): RatingCounts =
    RatingCounts(
        again = count { it.rating == Rating.AGAIN },
        fair = count { it.rating == Rating.FAIR },
        easy = count { it.rating == Rating.EASY },
    )
