package com.whatever.caro.study.internal.report

import com.whatever.caro.study.Rating
import com.whatever.caro.study.internal.studysession.ReviewLog
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import java.time.LocalDate

/**
 * 리포트 집계 쿼리. 모든 쿼리는 같은 조건을 쓴다.
 * - 로그와 세션 모두 사용자 본인 것만, 일일 학습(DAILY) 세션만, 기준 시점([maxReviewId]) 이하의 로그만 읽는다.
 * - deckId, 기간이 null이면 해당 조건을 적용하지 않는다.
 * 카드·덱의 삭제 여부는 조건에 넣지 않는다. 삭제 후에도 이력과 통계를 유지한다.
 */
interface StudyReportRepository : Repository<ReviewLog, Long> {
    @Query("select coalesce(max(rl.id), 0) from ReviewLog rl where rl.userId = :userId")
    fun findMaxReviewId(
        userId: Long,
    ): Long

    @Query(
        """
        select distinct ss.deckId from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
        """,
    )
    fun findHistoryDeckIds(
        userId: Long,
    ): List<Long>

    @Query(
        """
        select min(ss.sessionDate) from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and rl.id <= :maxReviewId
            and (:deckId is null or ss.deckId = :deckId)
        """,
    )
    fun findFirstStudyDate(
        userId: Long,
        deckId: Long?,
        maxReviewId: Long,
    ): LocalDate?

    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportCountsRow(
            count(rl.id),
            count(distinct rl.cardId),
            count(distinct ss.sessionDate),
            sum(case when rl.rating = com.whatever.caro.study.Rating.EASY then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.FAIR then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.AGAIN then 1 else 0 end)
        )
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and rl.id <= :maxReviewId
            and (:deckId is null or ss.deckId = :deckId)
            and (:startDate is null or ss.sessionDate >= :startDate)
            and (:endDate is null or ss.sessionDate <= :endDate)
        """,
    )
    fun countReviews(
        userId: Long,
        deckId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        maxReviewId: Long,
    ): ReportCountsRow

    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportDailyRow(
            ss.sessionDate,
            count(rl.id),
            sum(case when rl.rating = com.whatever.caro.study.Rating.EASY then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.FAIR then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.AGAIN then 1 else 0 end)
        )
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and rl.id <= :maxReviewId
            and (:deckId is null or ss.deckId = :deckId)
            and ss.sessionDate >= :startDate
            and ss.sessionDate <= :endDate
        group by ss.sessionDate
        order by ss.sessionDate asc
        """,
    )
    fun countReviewsByDate(
        userId: Long,
        deckId: Long?,
        startDate: LocalDate,
        endDate: LocalDate,
        maxReviewId: Long,
    ): List<ReportDailyRow>

    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportSessionRow(
            ss.id,
            ss.deckId,
            ss.sessionDate,
            ss.timezone,
            ss.status,
            count(rl.id),
            count(distinct rl.cardId),
            sum(case when rl.rating = com.whatever.caro.study.Rating.EASY then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.FAIR then 1 else 0 end),
            sum(case when rl.rating = com.whatever.caro.study.Rating.AGAIN then 1 else 0 end)
        )
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and rl.id <= :maxReviewId
            and (:deckId is null or ss.deckId = :deckId)
            and (:startDate is null or ss.sessionDate >= :startDate)
            and (:endDate is null or ss.sessionDate <= :endDate)
            and (
                :afterDate is null
                or ss.sessionDate < :afterDate
                or (ss.sessionDate = :afterDate and ss.id < :afterSessionId)
            )
        group by ss.id, ss.deckId, ss.sessionDate, ss.timezone, ss.status
        order by ss.sessionDate desc, ss.id desc
        """,
    )
    fun findSessions(
        userId: Long,
        deckId: Long?,
        startDate: LocalDate?,
        endDate: LocalDate?,
        afterDate: LocalDate?,
        afterSessionId: Long?,
        maxReviewId: Long,
        pageable: Pageable,
    ): List<ReportSessionRow>

    /**
     * 카드마다 기간 안의 마지막 평가 로그 한 건을 마지막 평가가 최신인 순서로 읽는다.
     * [lastRating]은 마지막 평가 행에 적용하므로, 기간 안에 그 평가를 한 번이라도 받은 카드가 아니라 마지막 평가가 그 값인 카드만 남는다.
     */
    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportCardLastRow(
            rl.cardId, rl.id, rl.rating, rl.createdAt, ss.sessionDate
        )
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and ss.deckId = :deckId
            and rl.id = (
                select max(r2.id) from ReviewLog r2 join r2.studySession s2
                where r2.cardId = rl.cardId
                    and r2.userId = :userId and s2.userId = :userId
                    and s2.studyType = com.whatever.caro.study.StudyType.DAILY
                    and s2.deckId = :deckId
                    and r2.id <= :maxReviewId
                    and (:startDate is null or s2.sessionDate >= :startDate)
                    and (:endDate is null or s2.sessionDate <= :endDate)
            )
            and (:lastRating is null or rl.rating = :lastRating)
            and (:afterReviewId is null or rl.id < :afterReviewId)
        order by rl.id desc
        """,
    )
    fun findCardLastReviews(
        userId: Long,
        deckId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        lastRating: Rating?,
        afterReviewId: Long?,
        maxReviewId: Long,
        pageable: Pageable,
    ): List<ReportCardLastRow>

    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportCardCountRow(rl.cardId, count(rl.id))
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and ss.deckId = :deckId
            and rl.cardId in :cardIds
            and rl.id <= :maxReviewId
            and (:startDate is null or ss.sessionDate >= :startDate)
            and (:endDate is null or ss.sessionDate <= :endDate)
        group by rl.cardId
        """,
    )
    fun countReviewsByCard(
        userId: Long,
        deckId: Long,
        cardIds: Collection<Long>,
        startDate: LocalDate?,
        endDate: LocalDate?,
        maxReviewId: Long,
    ): List<ReportCardCountRow>

    @Query(
        """
        select new com.whatever.caro.study.internal.report.ReportReviewRow(
            rl.id, ss.id, rl.rating, ss.sessionDate, rl.createdAt, ss.timezone, rl.reviewType
        )
        from ReviewLog rl join rl.studySession ss
        where rl.userId = :userId and ss.userId = :userId
            and ss.studyType = com.whatever.caro.study.StudyType.DAILY
            and ss.deckId = :deckId
            and rl.cardId = :cardId
            and rl.id <= :maxReviewId
            and (:startDate is null or ss.sessionDate >= :startDate)
            and (:endDate is null or ss.sessionDate <= :endDate)
            and (:afterReviewId is null or rl.id < :afterReviewId)
        order by rl.id desc
        """,
    )
    fun findCardReviews(
        userId: Long,
        deckId: Long,
        cardId: Long,
        startDate: LocalDate?,
        endDate: LocalDate?,
        afterReviewId: Long?,
        maxReviewId: Long,
        pageable: Pageable,
    ): List<ReportReviewRow>
}
