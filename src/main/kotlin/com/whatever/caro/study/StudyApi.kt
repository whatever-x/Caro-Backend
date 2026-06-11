package com.whatever.caro.study

import java.time.Instant
import java.time.ZoneId

// TODO StudySessionApi로 분리
interface StudyApi {
    /**
     * 카드별 LearningStates를 cardId 기준 맵으로 반환한다.
     */
    fun getLearningStates(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardLearningStateDto>

    fun startOrResumeDailyStudySession(
        now: Instant,
        userId: Long,
        deckId: Long,
        studyType: StudyType,
        timezone: ZoneId,
        dayCutoffHour: Int = 0,
    ): TodayStudySessionState

    fun getStudySessionCardQueue(
        userId: Long,
        sessionId: Long,
        now: Instant,
    ): List<CardLearningStateDto>

    fun getTodaySummaries(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckIds: Set<Long>,
    ): Map<Long, TodayStudySessionState>
}
