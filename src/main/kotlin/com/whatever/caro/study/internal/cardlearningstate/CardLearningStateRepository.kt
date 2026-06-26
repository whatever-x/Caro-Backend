package com.whatever.caro.study.internal.cardlearningstate

import com.whatever.caro.study.internal.DeckCardCount
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface CardLearningStateRepository : JpaRepository<CardLearningState, Long> {
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
}
