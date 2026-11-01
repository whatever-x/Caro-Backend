package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.internal.DeckCardCount
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface CardLearningStateRepository :
    JpaRepository<CardLearningState, Long>,
    CardLearningStateBulkRepository {
    fun findAllByUserIdAndCardIdIn(
        userId: Long,
        cardIds: Collection<Long>,
    ): List<CardLearningState>

    fun findAllByUserIdAndCardIdInAndDeletedAtIsNull(
        userId: Long,
        cardIds: Collection<Long>,
    ): List<CardLearningState>

    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.nextReviewDate <= :today
            and cls.status = CardLearningStatus.REVIEW
            and cls.deletedAt is null
    """,
    )
    fun countTodayReviewCards(
        userId: Long,
        deckId: Long,
        today: LocalDate,
    ): Int

    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and cls.deletedAt is null
    """,
    )
    fun countNewCards(
        userId: Long,
        deckId: Long,
    ): Int

    @Query(
        """
        select cls from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.REVIEW
            and cls.nextReviewDate <= :sessionDate
            and (cls.lastReviewedDate is null or cls.lastReviewedDate < :sessionDate)
            and cls.deletedAt is null
        order by cls.nextReviewDate asc, cls.id asc
    """,
    )
    fun findAllReviewCard(
        userId: Long,
        deckId: Long,
        sessionDate: LocalDate,
        pageable: Pageable,
    ): List<CardLearningState>

    @Query(
        """
        select cls from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and (cls.lastReviewedDate is null or cls.lastReviewedDate < :sessionDate)
            and cls.deletedAt is null
        order by cls.id asc
    """,
    )
    fun findAllNewCard(
        userId: Long,
        deckId: Long,
        pageable: Pageable,
        sessionDate: LocalDate,
    ): List<CardLearningState>

    /**
     * 오늘 학습하지 않은, NEW 상태인 LearningState를 반환
     */
    @Query(
        """
        select count(cls) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
            and (cls.lastReviewedDate is null or cls.lastReviewedDate < :today)
            and cls.deletedAt is null
        order by cls.id asc
        """,
    )
    fun countRemainingNewCards(
        userId: Long,
        deckId: Long,
        today: LocalDate,
    ): Int

    @Query(
        """
        select new com.whatever.caro.study.internal.DeckCardCount(cls.deckId, count(cls)) from CardLearningState cls
        where cls.deckId in :deckIds
            and cls.status = CardLearningStatus.NEW
            and cls.deletedAt is null
        group by cls.deckId
        """,
    )
    fun countNewCardsByDeckIds(
        deckIds: Collection<Long>,
    ): List<DeckCardCount>

    @Query(
        """
        select new com.whatever.caro.study.internal.DeckCardCount(cls.deckId, count(cls)) from CardLearningState cls
        where cls.deckId in :deckIds
            and cls.status = CardLearningStatus.REVIEW
            and cls.deletedAt is null
            and cls.nextReviewDate <= :today
        group by cls.deckId
        """,
    )
    fun countReviewCardsByDeckIds(
        deckIds: Collection<Long>,
        today: LocalDate,
    ): List<DeckCardCount>

    /**
     * 사용자에게 학습 대상 NEW 카드 존재 여부.
     */
    fun existsNewCardByUser(
        userId: Long,
    ): Boolean =
        existsByUserIdAndStatusAndDeletedAtIsNull(
            userId = userId,
            status = CardLearningStatus.NEW,
        )

    /**
     * 사용자에게 오늘 복습 대상(REVIEW, next_review_date < nextSessionStart) 카드 존재 여부.
     */
    fun existsTodayReviewCardByUser(
        userId: Long,
        nextSessionStart: LocalDate,
    ): Boolean =
        existsByUserIdAndStatusAndNextReviewDateLessThanAndDeletedAtIsNull(
            userId = userId,
            status = CardLearningStatus.REVIEW,
            nextReviewDate = nextSessionStart,
        )

    /**
     * 사용자에게 카드가 하나라도 존재하는지 여부.
     */
    fun existsByUserIdAndDeletedAtIsNull(
        userId: Long,
    ): Boolean

    fun existsByUserIdAndStatusAndDeletedAtIsNull(
        userId: Long,
        status: CardLearningStatus,
    ): Boolean

    fun existsByUserIdAndStatusAndNextReviewDateLessThanAndDeletedAtIsNull(
        userId: Long,
        status: CardLearningStatus,
        nextReviewDate: LocalDate,
    ): Boolean

    /**
     * 입력한 유저 중 오늘 학습(출제) 대상 카드가 있는 유저의 id를 반환한다.
     * 대상 = NEW 카드 또는 오늘까지 예정된 REVIEW 카드(nextReviewDate < nextSessionStart).
     * 복습 알림의 "복습할 카드 있음" 조건을 유저 집합에 대해 한 번에 판정한다(N+1 회피).
     */
    @Query(
        """
        select distinct cls.userId from CardLearningState cls
        where cls.userId in :userIds
            and cls.deletedAt is null
            and (
                cls.status = CardLearningStatus.NEW
                or (cls.status = CardLearningStatus.REVIEW and cls.nextReviewDate < :nextSessionStart)
            )
    """,
    )
    fun findUserIdsWithStudyToday(
        userIds: Collection<Long>,
        nextSessionStart: LocalDate,
    ): List<Long>

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CardLearningState cls where cls.userId = :userId")
    fun hardDeleteAllByUserId(
        userId: Long,
    ): Int
}
