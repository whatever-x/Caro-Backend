package com.whatever.caro.bff.internal

import com.whatever.caro.card.api.card.CardApi
import com.whatever.caro.card.api.deck.DeckApi
import com.whatever.caro.study.Rating
import com.whatever.caro.study.StudyReportApi
import com.whatever.caro.study.StudyReportPeriod
import com.whatever.caro.study.StudyReportSessionKey
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 학습 리포트 조회
 *
 * 평가 집계는 study에서, 덱 이름과 카드 본문은 card에서 읽어 합친다.
 * 카드·덱을 삭제해도 평가 이력과 통계는 그대로 두고, 삭제된 덱 이름과 카드 본문만 숨긴다.
 * 덱만 삭제하면 그 덱의 카드는 card 모듈에서 활성으로 남으므로, 덱이 삭제됐으면 카드 본문도 숨긴다.
 */
@Service
class StudyReportBFFService(
    private val studyReportApi: StudyReportApi,
    private val deckApi: DeckApi,
    private val cardApi: CardApi,
) {
    fun getSummary(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): ReportSummaryView {
        val today = now.atZone(timezone).toLocalDate()
        val period = resolvePeriod(today, startDate, endDate)
        val scope = resolveScope(userId, deckId)
        val maxReviewId = studyReportApi.getMaxReviewId(userId)

        val selected = studyReportApi.getCounts(userId, deckId, period, maxReviewId)
        // 전체 누적은 "전체"를 고른 경우에만 집계한다
        val lifetime = if (deckId == null) studyReportApi.getCounts(userId, null, null, maxReviewId) else null

        val rangeStart = period?.startDate
            ?: studyReportApi.getFirstStudyDate(userId, deckId, maxReviewId)?.let { minOf(it, today) }
        val rangeEnd = period?.endDate ?: today
        if (rangeStart == null) {
            return ReportSummaryView(
                scope = scope,
                range = ReportRange(startDate = null, endDate = rangeEnd, bucketUnit = ReportBucketUnit.DAY),
                lifetime = lifetime,
                selected = selected,
                buckets = emptyList(),
                restDates = emptyList(),
            )
        }

        val chartPeriod = StudyReportPeriod(rangeStart, rangeEnd)
        val unit = ReportBuckets.unitOf(rangeStart, rangeEnd)
        val dailyCounts = studyReportApi.getDailyCounts(userId, deckId, chartPeriod, maxReviewId)
        // 휴식일은 사용자 단위 기록이라 "전체"의 일 단위 차트에만 표시한다. 평가가 있는 날은 휴식으로 보지 않는다
        val restDates = if (deckId == null && unit == ReportBucketUnit.DAY) {
            val studiedDates = dailyCounts.map { it.studyDate }.toSet()
            (studyReportApi.getRestDates(userId, chartPeriod) - studiedDates).sorted()
        } else {
            emptyList()
        }

        return ReportSummaryView(
            scope = scope,
            range = ReportRange(startDate = rangeStart, endDate = rangeEnd, bucketUnit = unit),
            lifetime = lifetime,
            selected = selected,
            buckets = ReportBuckets.build(rangeStart, rangeEnd, unit, dailyCounts),
            restDates = restDates,
        )
    }

    /**
     * 조회 조건 시트의 덱 목록. 지금 가진 덱과 기록이 남은 삭제 덱을 deckId 내림차순으로 반환한다.
     * 맨 위 "전체"는 클라이언트가 넣는다.
     */
    fun getDeckOptions(
        userId: Long,
        cursor: String?,
        limit: Int,
    ): ReportPageView<ReportDeckOption> {
        val after = cursor?.let {
            ReportCursor.decode(it, ReportCursor.Kind.DECKS, NO_FILTER, keyCount = 1).keys[0].toLongOrNull()
                ?: throw InvalidReportCursorException("deck cursor has invalid key")
        }
        val activeDeckNames = deckApi.getDecks(userId).associate { it.id to it.name }
        val deckIds = (activeDeckNames.keys + studyReportApi.getHistoryDeckIds(userId))
            .filter { after == null || it < after }
            .sortedDescending()

        val items = deckIds.take(limit).map { id ->
            ReportDeckOption(deckId = id, name = activeDeckNames[id], deleted = id !in activeDeckNames)
        }
        val nextCursor = if (deckIds.size > limit) {
            ReportCursor(ReportCursor.Kind.DECKS, NO_FILTER, 0, listOf(items.last().deckId.toString())).encode()
        } else {
            null
        }
        return ReportPageView(items, nextCursor)
    }

    fun getSessions(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        cursor: String?,
        limit: Int,
    ): ReportPageView<ReportSessionItem> {
        val period = resolvePeriod(now.atZone(timezone).toLocalDate(), startDate, endDate)
        if (deckId != null) resolveScope(userId, deckId)
        val filter = ReportCursor.filterOf(deckId, period?.startDate, period?.endDate)
        val decoded = cursor?.let { ReportCursor.decode(it, ReportCursor.Kind.SESSIONS, filter, keyCount = 2) }
        val maxReviewId = decoded?.maxReviewId ?: studyReportApi.getMaxReviewId(userId)
        val after = decoded?.let {
            StudyReportSessionKey(
                studyDate = runCatching { LocalDate.parse(it.keys[0]) }
                    .getOrElse { throw InvalidReportCursorException("session cursor has invalid date") },
                sessionId = it.keys[1].toLongOrNull()
                    ?: throw InvalidReportCursorException("session cursor has invalid id"),
            )
        }

        val rows = studyReportApi.getSessions(userId, deckId, period, after, maxReviewId, limit + 1)
        val activeDeckNames = deckApi.getDecks(userId).associate { it.id to it.name }
        val items = rows.take(limit).map {
            ReportSessionItem(
                sessionId = it.sessionId,
                deckId = it.deckId,
                deckName = activeDeckNames[it.deckId],
                deckDeleted = it.deckId !in activeDeckNames,
                studyDate = it.studyDate,
                timezone = it.timezone,
                status = it.status,
                counts = it.counts,
            )
        }
        val nextCursor = if (rows.size > limit) {
            val last = items.last()
            ReportCursor(
                ReportCursor.Kind.SESSIONS,
                filter,
                maxReviewId,
                listOf(last.studyDate.toString(), last.sessionId.toString()),
            ).encode()
        } else {
            null
        }
        return ReportPageView(items, nextCursor)
    }

    fun getCards(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        lastRating: Rating?,
        cursor: String?,
        limit: Int,
    ): ReportPageView<ReportCardItem> {
        val period = resolvePeriod(now.atZone(timezone).toLocalDate(), startDate, endDate)
        val scope = resolveScope(userId, deckId)
        val filter = ReportCursor.filterOf(deckId, period?.startDate, period?.endDate, lastRating)
        val decoded = cursor?.let { ReportCursor.decode(it, ReportCursor.Kind.CARDS, filter, keyCount = 1) }
        val maxReviewId = decoded?.maxReviewId ?: studyReportApi.getMaxReviewId(userId)
        val afterReviewId = decoded?.let {
            it.keys[0].toLongOrNull() ?: throw InvalidReportCursorException("card cursor has invalid id")
        }

        val rows = studyReportApi.getCards(userId, deckId, period, lastRating, afterReviewId, maxReviewId, limit + 1)
        val page = rows.take(limit)
        val labels = cardLabels(userId, page.map { it.cardId }, deckDeleted = scope.deleted)
        val items = page.map {
            ReportCardItem(
                card = labels.getValue(it.cardId),
                reviewCount = it.reviewCount,
                lastRating = it.lastRating,
                lastReviewedAt = it.lastReviewedAt,
                lastStudyDate = it.lastStudyDate,
            )
        }
        val nextCursor = if (rows.size > limit) {
            ReportCursor(
                ReportCursor.Kind.CARDS,
                filter,
                maxReviewId,
                listOf(page.last().lastReviewId.toString()),
            ).encode()
        } else {
            null
        }
        return ReportPageView(items, nextCursor)
    }

    fun getCardReviews(
        now: Instant,
        timezone: ZoneId,
        userId: Long,
        deckId: Long,
        cardId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        cursor: String?,
        limit: Int,
    ): ReportCardReviewsView {
        val today = now.atZone(timezone).toLocalDate()
        val period = resolvePeriod(today, startDate, endDate)
        val scope = resolveScope(userId, deckId)
        val filter = ReportCursor.filterOf(deckId, cardId, period?.startDate, period?.endDate)
        val decoded = cursor?.let { ReportCursor.decode(it, ReportCursor.Kind.REVIEWS, filter, keyCount = 1) }
        val maxReviewId = decoded?.maxReviewId ?: studyReportApi.getMaxReviewId(userId)
        val afterReviewId = decoded?.let {
            it.keys[0].toLongOrNull() ?: throw InvalidReportCursorException("review cursor has invalid id")
        }

        // 이 덱에서 평가한 적이 없는 카드는 다른 사용자의 카드와 구분하지 않고 404로 응답한다
        if (studyReportApi.getCardReviews(userId, deckId, cardId, null, null, maxReviewId, 1).isEmpty()) {
            throw ReportTargetNotFoundException("cardId=$cardId has no review in deckId=$deckId")
        }

        val rows = studyReportApi.getCardReviews(userId, deckId, cardId, period, afterReviewId, maxReviewId, limit + 1)
        val items = rows.take(limit).map {
            ReportReviewItem(
                reviewId = it.reviewId,
                sessionId = it.sessionId,
                rating = it.rating,
                studyDate = it.studyDate,
                reviewedAt = it.reviewedAt,
                timezone = it.timezone,
                reviewType = it.reviewType,
            )
        }
        val nextCursor = if (rows.size > limit) {
            ReportCursor(
                ReportCursor.Kind.REVIEWS,
                filter,
                maxReviewId,
                listOf(items.last().reviewId.toString()),
            ).encode()
        } else {
            null
        }
        val rangeStart = period?.startDate
            ?: studyReportApi.getFirstStudyDate(userId, deckId, maxReviewId)?.let { minOf(it, today) }
        val rangeEnd = period?.endDate ?: today
        return ReportCardReviewsView(
            card = cardLabels(userId, listOf(cardId), deckDeleted = scope.deleted).getValue(cardId),
            range = ReportRange(
                startDate = rangeStart,
                endDate = rangeEnd,
                bucketUnit = rangeStart?.let { ReportBuckets.unitOf(it, rangeEnd) } ?: ReportBucketUnit.DAY,
            ),
            page = ReportPageView(items, nextCursor),
        )
    }

    /**
     * 기간을 정한다. 두 날짜를 모두 생략하면 전체 기간(null)이다.
     */
    private fun resolvePeriod(
        today: LocalDate,
        startDate: LocalDate?,
        endDate: LocalDate?,
    ): StudyReportPeriod? {
        if (startDate == null && endDate == null) return null
        if (startDate == null || endDate == null) {
            throw InvalidReportPeriodException("startDate and endDate must be given together")
        }
        if (startDate.isAfter(endDate)) throw InvalidReportPeriodException("startDate is after endDate")
        if (endDate.isAfter(today)) throw InvalidReportPeriodException("endDate($endDate) is after today($today)")
        return StudyReportPeriod(startDate, endDate)
    }

    /**
     * 지금 가진 덱이거나 본인의 평가 기록이 남은 덱만 조회할 수 있다.
     */
    private fun resolveScope(
        userId: Long,
        deckId: Long?,
    ): ReportScope {
        if (deckId == null) return ReportScope(deckId = null, deckName = null, deleted = false)
        deckApi.getDecks(userId).firstOrNull { it.id == deckId }?.let {
            return ReportScope(deckId = it.id, deckName = it.name, deleted = false)
        }
        if (deckId in studyReportApi.getHistoryDeckIds(userId)) {
            return ReportScope(deckId = deckId, deckName = null, deleted = true)
        }
        throw ReportTargetNotFoundException("deckId=$deckId is not accessible")
    }

    private fun cardLabels(
        userId: Long,
        cardIds: List<Long>,
        deckDeleted: Boolean,
    ): Map<Long, ReportCardLabel> {
        val contents = if (deckDeleted || cardIds.isEmpty()) emptyMap() else cardApi.getCardsByIds(userId, cardIds)
        return cardIds.associateWith { id ->
            val fields = contents[id]?.fields
            ReportCardLabel(
                cardId = id,
                deleted = fields == null,
                front = fields?.get(FRONT_FIELD),
                back = fields?.get(BACK_FIELD),
            )
        }
    }

    companion object {
        private const val NO_FILTER = "-"
        private const val FRONT_FIELD = "front"
        private const val BACK_FIELD = "back"
    }
}
