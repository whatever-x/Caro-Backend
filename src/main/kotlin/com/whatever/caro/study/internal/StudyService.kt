package com.whatever.caro.study.internal

import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyTargetPoolCalculator
import com.whatever.caro.study.StudyTargetPoolCount
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.exception.SessionNotFoundException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.streak.StreakStateRepository
import com.whatever.caro.study.internal.streak.StudyDayRepository
import com.whatever.caro.study.internal.studysession.ReviewLogRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

@Service
class StudyService(
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val reviewLogRepository: ReviewLogRepository,
    private val studyDayRepository: StudyDayRepository,
    private val streakStateRepository: StreakStateRepository,
    private val deckPresetApi: DeckPresetApi,
    private val studyTargetPoolCalculator: StudyTargetPoolCalculator,
) : StudyApi {

    @Transactional
    override fun deleteAllByUserId(
        userId: Long,
    ) {
        // FK 순서: review_logs(자식, study_session_id) → study_sessions(부모). 나머지는 독립.
        reviewLogRepository.hardDeleteAllByUserId(userId)
        studySessionRepository.hardDeleteAllByUserId(userId)
        cardLearningStateRepository.hardDeleteAllByUserId(userId)
        studyDayRepository.hardDeleteAllByUserId(userId)
        streakStateRepository.hardDeleteAllByUserId(userId)
    }

    @Transactional
    override fun startOrResumeDailyStudySession(
        now: Instant,
        userId: Long,
        deckId: Long,
        studyType: StudyType,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): TodayStudySessionState {
        findTodaySession(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        )?.let { session ->
            if (session.status == StudySessionStatus.ACTIVE) {
                recalcGoals(session, now)
            }
            return session.toTodayState()
        }

        val target = findTodayCardPool(now, timezone, userId, deckId)
            ?: return TodayStudySessionState.RestDay

        val studySession = StudySession(
            userId = userId,
            deckId = deckId,
            status = StudySessionStatus.ACTIVE,
            studyType = studyType,
            startedAt = now,
            newCardsGoal = target.pool.newCount,
            reviewCardsGoal = target.pool.reviewCount,
            timezone = timezone,
            dayCutoffHour = dayCutoffHour,
            deckPresetIdSnapshot = target.presetId,
        )
        studySessionRepository.save(studySession)
        return TodayStudySessionState.InProgress(studySession.toDto())
    }

    @Transactional(readOnly = true)
    override fun getStudySessionCardQueue(
        userId: Long,
        sessionId: Long,
        now: Instant,
        timezone: ZoneId,
    ): List<CardLearningStateDto> {
        val todaySession = studySessionRepository.findByIdAndUserId(
            id = sessionId,
            userId = userId,
        ) ?: throw SessionNotFoundException()

        if (!todaySession.isTodaySession(now, timezone)) {
            throw SessionExpiredException()
        }

        val newPoolSize = todaySession.newCardsGoal - todaySession.newCardsStudied
        val reviewPoolSize = todaySession.reviewCardsGoal - todaySession.reviewCardsStudied

        val newCardQueue = if (newPoolSize > 0) {
            cardLearningStateRepository.findAllNewCard(
                userId = userId,
                deckId = todaySession.deckId,
                pageable = PageRequest.ofSize(newPoolSize),
                sessionDate = todaySession.sessionDate,
            )
        } else {
            emptyList()
        }
        val reviewCardQueue = if (reviewPoolSize > 0) {
            cardLearningStateRepository.findAllReviewCard(
                userId = userId,
                deckId = todaySession.deckId,
                sessionDate = todaySession.sessionDate,
                pageable = PageRequest.ofSize(reviewPoolSize),
            )
        } else {
            emptyList()
        }

        return newCardQueue.map { it.toDto() } + reviewCardQueue.map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getTodaySummary(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ): TodayStudySessionState {
        findTodaySession(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        )?.let { return it.toTodayState() }

        val target = findTodayCardPool(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        ) ?: return TodayStudySessionState.RestDay

        return TodayStudySessionState.NotStarted(
            pool = target.pool,
            presetId = target.presetId,
        )
    }

    @Transactional
    fun adjustGoalsOnCardDeletion(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ) {
        val session = findTodaySession(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        )
            ?.takeIf { it.status == StudySessionStatus.ACTIVE }
            ?: return

        recalcGoals(session, now)
    }

    private fun findTodaySession(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ): StudySession? {
        val nowDate = now.atZone(timezone).toLocalDate()
        return studySessionRepository.findByUserIdAndDeckIdAndSessionDateBetween(
            userId = userId,
            deckId = deckId,
            fromDate = nowDate.minusDays(1L), // cutoff 23시간 고려
            toDate = nowDate,
        )
            .sortedByDescending { it.sessionDate }
            .firstOrNull { it.isTodaySession(now, timezone) }
    }

    private fun findTodayCardPool(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ): TodayCardPool? {
        val preset = deckPresetApi.getLatestDeckPresetByUser(deckId, userId)
        val todayPool = studyTargetPoolCalculator.getTodayPool(
            now = now,
            userId = userId,
            timezone = timezone,
            deckId = deckId,
            newCardPerDay = preset.newPerDay,
            reviewCardPerDay = preset.reviewPerDay,
            dayCutoffHour = 0,
        )
        return if (todayPool.newCount == 0 && todayPool.reviewCount == 0) {
            null
        } else {
            TodayCardPool(todayPool, preset.id)
        }
    }

    override fun getLearningStates(
        userId: Long,
        cardIds: Collection<Long>,
    ): Map<Long, CardLearningStateDto> {
        val learningStates = cardLearningStateRepository.findAllByUserIdAndCardIdInAndDeletedAtIsNull(userId, cardIds)
        return learningStates.associate { it.cardId to it.toDto() }
    }

    @Transactional(readOnly = true)
    override fun getTodaySummaries(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckIds: Set<Long>,
    ): Map<Long, TodayStudySessionState> {
        if (deckIds.isEmpty()) {
            return emptyMap()
        }

        val nowDate = now.atZone(timezone).toLocalDate()
        val todaySessionByDeckId = studySessionRepository.findByUserIdAndDeckIdInAndSessionDateBetween(
            userId = userId,
            deckIds = deckIds,
            fromDate = nowDate.minusDays(1L), // cutoff 23시간 고려
            toDate = nowDate,
        )
            .filter { it.isTodaySession(now = now, clientTimezone = timezone) }
            .sortedByDescending { it.sessionDate }
            .associateBy { it.deckId }

        val noSessionDeckIds = deckIds - todaySessionByDeckId.keys
        val presetByDeckId = if (noSessionDeckIds.isNotEmpty()) {
            deckPresetApi.getLatestDeckPresetsByDeckId(
                userId = userId,
                deckIds = noSessionDeckIds,
            )
        } else {
            emptyMap()
        }
        val todayPoolByDeckId = studyTargetPoolCalculator.getTodayPools(
            now = now,
            timezone = timezone,
            presetByDeckId = presetByDeckId,
            dayCutoffHour = 0,
        )

        return deckIds.associateWith { deckId ->
            todaySessionByDeckId[deckId]?.toTodayState() ?: run {
                val preset = checkNotNull(presetByDeckId[deckId]) { "deck에 연결된 preset이 없습니다. deckId=$deckId" }
                val pool = todayPoolByDeckId.getValue(deckId)
                if (pool.newCount == 0 && pool.reviewCount == 0) {
                    TodayStudySessionState.RestDay
                } else {
                    TodayStudySessionState.NotStarted(pool, preset.id)
                }
            }
        }
    }

    private fun recalcGoals(
        session: StudySession,
        now: Instant,
    ) {
        val availableNewCount = cardLearningStateRepository.countRemainingNewCards(
            userId = session.userId,
            deckId = session.deckId,
            today = session.sessionDate,
        )
        val availableReviewCount = cardLearningStateRepository.countTodayReviewCards(
            userId = session.userId,
            deckId = session.deckId,
            today = session.sessionDate,
        )
        session.recalculateGoals(
            availableNewGoal = availableNewCount,
            availableReviewGoal = availableReviewCount,
        )
        session.completeIfGoalAchieved(now)
    }
}

private class TodayCardPool(
    val pool: StudyTargetPoolCount,
    val presetId: Long,
)

private fun StudySession.toTodayState(): TodayStudySessionState =
    when (status) {
        StudySessionStatus.ACTIVE -> TodayStudySessionState.InProgress(toDto())
        else -> TodayStudySessionState.Completed(toDto()) // STOPPED도 종료된 상태로 매핑해 반환
    }

private fun CardLearningState.toDto(): CardLearningStateDto =
    CardLearningStateDto(
        cardId = cardId,
        status = status,
        totalReviews = totalReviews,
        consecutiveAgainCount = consecutiveAgainCount,
        lastReviewedDate = lastReviewedDate,
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
