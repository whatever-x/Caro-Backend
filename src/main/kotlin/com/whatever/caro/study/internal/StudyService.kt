package com.whatever.caro.study.internal

import com.whatever.caro.card.DeckPresetApi
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionCardQueueDto
import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyTargetPoolCalculator
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodaySummaryDto
import com.whatever.caro.study.TodaySummaryState
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.exception.SessionNotFoundException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Service
class StudyService(
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val deckPresetApi: DeckPresetApi,
    private val studyTargetPoolCalculator: StudyTargetPoolCalculator,
) : StudyApi {

    @Transactional
    fun startOrResumeDailyStudySession(
        now: Instant,
        userId: Long,
        deckId: Long,
        studyType: StudyType,
        timezone: ZoneId,
        dayCutoffHour: Int = 0,
    ): StudySessionDto {
        val todaySession = getTodayStudySession(now, userId, deckId)
        if (todaySession != null) {
            return todaySession
        }

        val preset = deckPresetApi.getLatestDeckPresetByUser(deckId, userId)

        val todayPool = studyTargetPoolCalculator.getTodayPool(
            now,
            userId,
            timezone,
            deckId,
            preset.newPerDay,
            preset.reviewPerDay,
            dayCutoffHour,
        )

        val studySession = StudySession(
            userId = userId,
            deckId = deckId,
            status = StudySessionStatus.ACTIVE,
            studyType = studyType,
            startedAt = now,
            newCardsGoal = todayPool.newCount,
            reviewCardsGoal = todayPool.reviewCount,
            timezone = timezone,
            dayCutoffHour = dayCutoffHour,
            deckPresetIdSnapshot = preset.id,
        )
        studySessionRepository.save(studySession)
        return studySession.toDto()
    }

    @Transactional(readOnly = true)
    fun getStudySessionCardQueue(
        userId: Long,
        sessionId: Long,
        now: Instant,
    ): StudySessionCardQueueDto {
        val todaySession = studySessionRepository.findByIdAndUserId(
            id = sessionId,
            userId = userId,
        ) ?: throw SessionNotFoundException()

        if (!todaySession.isTodaySession(now)) {
            throw SessionExpiredException()
        }

        val newPoolSize = todaySession.newCardsGoal - todaySession.newCardsStudied
        val reviewPoolSize = todaySession.reviewCardsGoal - todaySession.reviewCardsStudied

        val newCardQueue = if (newPoolSize > 0) {
            // 같은 세션에서 `AGAIN(모르겠어요)` 평가 시, 해당 쿼리에서 재조회되므로 주의 필요
            cardLearningStateRepository.findAllNewCard(
                userId = userId,
                deckId = todaySession.deckId,
                pageable = PageRequest.ofSize(newPoolSize),
            )
        } else {
            emptyList()
        }
        val reviewCardQueue = if (reviewPoolSize > 0) {
            cardLearningStateRepository.findAllReviewCard(
                userId = userId,
                deckId = todaySession.deckId,
                sessionStart = todaySession.sessionStart,
                nextSessionStart = todaySession.nextSessionStart,
                pageable = PageRequest.ofSize(reviewPoolSize),
            )
        } else {
            emptyList()
        }

        return StudySessionCardQueueDto(
            newQueue = newCardQueue.map { it.toDto() },
            reviewQueue = reviewCardQueue.map { it.toDto() },
        )
    }

    override fun getTodaySummary(
        now: Instant,
        userId: Long,
        deckId: Long,
    ): TodaySummaryDto {
        val latestSession = studySessionRepository.findByUserAndDeckOrderByStartedAtDesc(userId, deckId)
            ?: return TodaySummaryDto.notStarted()

        if (latestSession.isTodaySession(now).not()) {
            handleStaleSession(latestSession)
            return TodaySummaryDto.notStarted()
        }

        return when (latestSession.status) {
            StudySessionStatus.ACTIVE -> latestSession.toTodaySummaryDto(TodaySummaryState.IN_PROGRESS)
            StudySessionStatus.COMPLETED -> latestSession.toTodaySummaryDto(TodaySummaryState.COMPLETED)
            StudySessionStatus.STOPPED -> TodaySummaryDto.notStarted()
        }
    }

    private fun handleStaleSession(
        latestSession: StudySession,
    ) {
        val effectedRow = studySessionRepository.setStoppedIfActive(latestSession.id)
        logger.warn {
            "Stale active study session detected. sessionId=${latestSession.id} effected row: $effectedRow"
        }
    }

    private fun getTodayStudySession(
        now: Instant,
        userId: Long,
        deckId: Long,
    ): StudySessionDto? {
        val latestSession = studySessionRepository.findByUserAndDeckOrderByStartedAtDesc(userId, deckId) ?: return null

        return when (latestSession.status) {
            StudySessionStatus.ACTIVE -> {
                if (latestSession.isTodaySession(now)) {
                    latestSession.toDto()
                } else {
                    val effectedRow = studySessionRepository.setStoppedIfActive(latestSession.id)
                    logger.warn {
                        "Stale active study session detected. sessionId=${latestSession.id} effected row: $effectedRow"
                    }
                    null
                }
            }

            StudySessionStatus.COMPLETED -> {
                if (latestSession.isTodaySession(now)) {
                    latestSession.toDto()
                } else {
                    null
                }
            }

            StudySessionStatus.STOPPED -> null
        }
    }

    override fun getLearningStates(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardLearningStateDto> {
        val learningStates = cardLearningStateRepository.findAllByUserIdAndCardIdIn(userId, cardIds)
        return learningStates.associate { it.cardId to it.toDto() }
    }
}

private fun StudySession.toTodaySummaryDto(
    state: TodaySummaryState,
): TodaySummaryDto =
    TodaySummaryDto(
        state = state,
        studiedCardCount = newCardsStudied + reviewCardsStudied,
        totalCardCount = estimatedTotal,
        sessionId = id,
    )

private fun CardLearningState.toDto(): CardLearningStateDto =
    CardLearningStateDto(
        cardId = cardId,
        totalReviews = totalReviews,
        consecutiveAgainCount = consecutiveAgainCount,
    )

private fun StudySession.toDto(): StudySessionDto =
    StudySessionDto(
        sessionId = id,
        deckId = deckId,
        status = status,
        studyType = studyType,
        sessionDate = sessionDate,
        newCardsStudied = newCardsStudied,
        reviewCardsStudied = reviewCardsStudied,
        newCardsGoal = newCardsGoal,
        reviewCardsGoal = reviewCardsGoal,
        estimatedTotal = estimatedTotal,
        startedAt = startedAt,
        endedAt = endedAt,
    )
