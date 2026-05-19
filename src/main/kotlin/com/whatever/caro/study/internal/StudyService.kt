package com.whatever.caro.study.internal

import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.TodaySummaryDto
import com.whatever.caro.study.TodaySummaryState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant

private val logger = KotlinLogging.logger {}

@Service
class StudyService(
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
) : StudyApi {

    override fun getTodaySummary(
        now: Instant,
        userId: Long,
        deckId: Long,
    ): TodaySummaryDto? {
        val latestSession = studySessionRepository.findByUserAndDeckOrderByStartedAtDesc(userId, deckId) ?: return null

        return when (latestSession.status) {
            StudySessionStatus.ACTIVE -> {
                if (latestSession.isTodaySession(now)) {
                    latestSession.toTodaySummaryDto(TodaySummaryState.IN_PROGRESS)
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
                    latestSession.toTodaySummaryDto(TodaySummaryState.COMPLETED)
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
