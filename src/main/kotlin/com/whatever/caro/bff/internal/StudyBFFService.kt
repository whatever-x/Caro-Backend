package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
import io.github.oshai.kotlinlogging.KotlinLogging
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneId

private val logger = KotlinLogging.logger {}

@Service
class StudyBFFService(
    private val studyApi: StudyApi,
    private val cardApi: CardApi,
) {
    fun startOrResumeDailyStudy(
        now: Instant,
        userId: Long,
        deckId: Long,
        timezone: ZoneId,
    ): DailyStudyView {
        val sessionState = studyApi.startOrResumeDailyStudySession(
            now = now,
            userId = userId,
            deckId = deckId,
            studyType = StudyType.DAILY,
            timezone = timezone,
            dayCutoffHour = 0,
        )

        return when (sessionState) {
            is TodayStudySessionState.InProgress -> resolveInProgress(sessionState, userId, now)
            is TodayStudySessionState.Completed -> DailyStudyView.CompletedDto(
                sessionId = sessionState.session.sessionId,
                totalCardCount = sessionState.session.estimatedTotal,
                studiedCardCount = sessionState.session.newCardsStudied + sessionState.session.reviewCardsStudied,
            )
            is TodayStudySessionState.RestDay -> DailyStudyView.RestDayDto
            is TodayStudySessionState.NotStarted -> error("`NotStarted` status session is not allowed")
        }
    }

    private fun resolveInProgress(
        sessionState: TodayStudySessionState.InProgress,
        userId: Long,
        now: Instant
    ): DailyStudyView.InProgressDto {
        val sessionId = sessionState.session.sessionId
        val cardQueue = studyApi.getStudySessionCardQueue(
            userId = userId,
            sessionId = sessionId,
            now = now,
        )

        val cardIds = cardQueue.map { it.cardId }
        val contentById = cardApi.getCardsByIds(
            userId = userId,
            cardIds = cardIds,
        )

        val cards = cardQueue.mapNotNull { cls ->
            contentById[cls.cardId]?.let {
                StudyCardItem(cardId = it.cardId, fields = it.fields)
            }
        }

        val missing = cardIds - contentById.keys
        if (missing.isNotEmpty()) {
            logger.warn { "orphan card detected: sessionId=$sessionId, cardIds=$missing" }
            throw RuntimeException()  // TODO Custom Exception
        }

        return DailyStudyView.InProgressDto(
            sessionId = sessionId,
            totalCardCount = sessionState.session.estimatedTotal,
            studiedCardCount = sessionState.session.newCardsStudied + sessionState.session.reviewCardsStudied,
            cards = cards
        )
    }
}
