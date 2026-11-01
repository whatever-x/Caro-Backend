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
        timezone: ZoneId,
    ): List<CardLearningStateDto>

    fun getTodaySummaries(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckIds: Set<Long>,
    ): Map<Long, TodayStudySessionState>

    /**
     * 입력한 유저 중 "오늘 학습(복습)할 카드가 있는" 유저의 id를 반환한다.
     * 오늘 출제 대상 = NEW 카드 또는 오늘까지 예정된 REVIEW 카드. (휴식일 판정의 역)
     * 복습 알림이 "설정 시각에 복습할 카드가 있을 때만" 발송하는 조건 판정에 쓴다.
     */
    fun findUserIdsWithStudyToday(
        userIds: Collection<Long>,
        now: Instant,
        timezone: ZoneId,
        dayCutoffHour: Int = 0,
    ): Set<Long>

    fun deleteAllByUserId(
        userId: Long,
    )
}
