package com.whatever.caro.study.internal

import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStateDto
import com.whatever.caro.study.StudyApi
import com.whatever.caro.study.StudySessionDto
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyTargetPoolCalculator
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
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
    override fun startOrResumeDailyStudySession(
        // TODO 동시성 고려
        now: Instant,
        userId: Long,
        deckId: Long,
        studyType: StudyType,
        timezone: ZoneId,
        dayCutoffHour: Int,
    ): TodayStudySessionState {
        cleanupStaleSession(
            now = now,
            userId = userId,
            deckId = deckId,
        )

        val todayStudy = resolveTodayStudy(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        )
        return when (todayStudy) {
            is TodayStudySessionState.NotStarted -> {
                val studySession = StudySession(
                    userId = userId,
                    deckId = deckId,
                    status = StudySessionStatus.ACTIVE,
                    studyType = studyType,
                    startedAt = now,
                    newCardsGoal = todayStudy.pool.newCount,
                    reviewCardsGoal = todayStudy.pool.reviewCount,
                    timezone = timezone,
                    dayCutoffHour = dayCutoffHour,
                    deckPresetIdSnapshot = todayStudy.presetId,
                )
                studySessionRepository.save(studySession)
                TodayStudySessionState.InProgress(studySession.toDto())
            }

            else -> todayStudy
        }
    }

    @Transactional(readOnly = true)
    override fun getStudySessionCardQueue(
        userId: Long,
        sessionId: Long,
        now: Instant,
    ): List<CardLearningStateDto> {
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
            cardLearningStateRepository.findAllNewCard(
                userId = userId,
                deckId = todaySession.deckId,
                pageable = PageRequest.ofSize(newPoolSize),
                sessionStart = todaySession.sessionStart,
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

        return newCardQueue.map { it.toDto() } + reviewCardQueue.map { it.toDto() }
    }

    @Transactional(readOnly = true)
    fun getTodaySummary(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ): TodayStudySessionState =
        resolveTodayStudy(
            now = now,
            timezone = timezone,
            userId = userId,
            deckId = deckId,
        )

    @Transactional
    fun adjustGoalsOnCardDeletion(
        now: Instant,
        userId: Long,
        deckId: Long,
    ) {
        val session = findTodaySession(now = now, userId = userId, deckId = deckId)
            ?.takeIf { it.status == StudySessionStatus.ACTIVE }
            ?: return

        val availableNewCount = cardLearningStateRepository.countRemainingNewCards(
            userId = userId,
            deckId = deckId,
            sessionStart = session.sessionStart,
        )
        val availableReviewCount = cardLearningStateRepository.countTodayReviewCards(
            userId = userId,
            deckId = deckId,
            nextSessionStart = session.nextSessionStart,
        )
        session.recalculateGoals(
            availableNewGoal = availableNewCount,
            availableReviewGoal = availableReviewCount,
        )
        session.completeIfGoalAchieved(now)
    }

    private fun resolveTodayStudy(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
    ): TodayStudySessionState {
        findTodaySession(
            now = now,
            userId = userId,
            deckId = deckId,
        )?.let {
            return if (it.status == StudySessionStatus.ACTIVE) {
                TodayStudySessionState.InProgress(it.toDto())
            } else {
                TodayStudySessionState.Completed(it.toDto())
            }
        }

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
        if (todayPool.newCount == 0 && todayPool.reviewCount == 0) {
            return TodayStudySessionState.RestDay
        }
        return TodayStudySessionState.NotStarted(todayPool, preset.id)
    }

    private fun findTodaySession(
        now: Instant,
        userId: Long,
        deckId: Long,
    ): StudySession? {
        val latest = studySessionRepository.findByUserAndDeckOrderByStartedAtDesc(userId, deckId) ?: return null
        return when (latest.status) {
            StudySessionStatus.ACTIVE -> if (latest.isTodaySession(now)) latest else null
            StudySessionStatus.COMPLETED -> if (latest.isTodaySession(now)) latest else null
            StudySessionStatus.STOPPED -> null
        }
    }

    private fun cleanupStaleSession(
        now: Instant,
        userId: Long,
        deckId: Long,
    ) {
        val latestSession = studySessionRepository.findByUserAndDeckOrderByStartedAtDesc(userId, deckId) ?: return
        if (latestSession.status == StudySessionStatus.ACTIVE && !latestSession.isTodaySession(now)) {
            val effectedRow = studySessionRepository.setStoppedIfActive(latestSession.id)
            logger.warn {
                "Stale active study session detected. sessionId=${latestSession.id} effected row: $effectedRow"
            }
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

        val todaySessionByDeckId = studySessionRepository.findLatestByUserIdAndDeckIdIn(
            userId,
            deckIds,
        ).filter { session ->
            when (session.status) {
                StudySessionStatus.ACTIVE, StudySessionStatus.COMPLETED -> session.isTodaySession(now)
                StudySessionStatus.STOPPED -> false
            }
        }.associateBy { it.deckId }

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
            todaySessionByDeckId[deckId]?.let { session ->
                if (session.status == StudySessionStatus.ACTIVE) {
                    TodayStudySessionState.InProgress(session.toDto())
                } else {
                    TodayStudySessionState.Completed(session.toDto())
                }
            } ?: run {
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
}

private fun CardLearningState.toDto(): CardLearningStateDto =
    CardLearningStateDto(
        cardId = cardId,
        status = status,
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
