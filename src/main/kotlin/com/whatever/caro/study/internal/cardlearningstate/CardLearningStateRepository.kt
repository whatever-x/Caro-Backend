package com.whatever.caro.study.internal.cardlearningstate

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface CardLearningStateRepository : JpaRepository<CardLearningState, Long> {
    fun findAllByUserIdAndCardIdIn(
        userId: Long,
        cardIds: Collection<Long>,
    ): List<CardLearningState>

    @Query(
        """
        select count(*) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.nextReviewAt < :nextSessionStart
            and cls.status = CardLearningStatus.REVIEW
    """,
    )
    fun countTodayReviewCards(
        userId: Long,
        deckId: Long,
        nextSessionStart: Instant,
    ): Int

    @Query(
        """
        select count(*) from CardLearningState cls
        where cls.userId = :userId
            and cls.deckId = :deckId
            and cls.status = CardLearningStatus.NEW
    """,
    )
    fun countNewCards(
        userId: Long,
        deckId: Long,
    ): Int
}
