package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionDto
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
            is TodayStudySessionState.InProgress -> resolveInProgress(sessionState, userId, now, timezone)
            is TodayStudySessionState.Completed -> sessionState.session.toCompletedView()
            is TodayStudySessionState.RestDay -> DailyStudyView.RestDayDto
            is TodayStudySessionState.NotStarted -> error("`NotStarted` status session is not allowed")
        }
    }

    private fun resolveInProgress(
        sessionState: TodayStudySessionState.InProgress,
        userId: Long,
        now: Instant,
        timezone: ZoneId,
    ): DailyStudyView {
        val sessionId = sessionState.session.sessionId
        val cardQueue = studyApi.getStudySessionCardQueue(
            userId = userId,
            sessionId = sessionId,
            now = now,
            timezone = timezone,
        )
        if (cardQueue.isEmpty()) {
            logger.warn { "empty card queue on IN_PROGRESS study session. sessionId: $sessionId" }
            return sessionState.session.toCompletedView()
        }

        val cardIds = cardQueue.map { it.cardId }
        val contentById = cardApi.getCardsByIds(
            userId = userId,
            cardIds = cardIds,
        )
        val missing = cardIds - contentById.keys
        if (missing.isNotEmpty()) { // 이 상황에서 로그만 남기고 진행하는게 학습 연속성 측면에서 좋을 것 같아 예외를 던지지 않았음
            logger.error { "orphan card detected: sessionId=$sessionId, cardIds=$missing" }
        }

        val cards = cardQueue.mapNotNull { cls ->
            contentById[cls.cardId]?.let {
                StudyCardItem(cardId = it.cardId, fields = it.fields)
            }
        }

        return DailyStudyView.InProgressDto(
            sessionId = sessionId,
            totalCardCount = sessionState.session.estimatedTotal,
            studiedCardCount = sessionState.session.newCardsStudied + sessionState.session.reviewCardsStudied,
            cards = cards,
        )
    }
}

private fun StudySessionDto.toCompletedView() =
    DailyStudyView.CompletedDto(
        sessionId = sessionId,
        totalCardCount = estimatedTotal,
        studiedCardCount = newCardsStudied + reviewCardsStudied,
    )
