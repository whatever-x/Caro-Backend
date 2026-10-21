package com.whatever.caro.study.internal.report

import com.whatever.caro.study.Rating
import com.whatever.caro.study.StreakType
import com.whatever.caro.study.StudyReportApi
import com.whatever.caro.study.StudyReportCardDto
import com.whatever.caro.study.StudyReportCountsDto
import com.whatever.caro.study.StudyReportDailyCountDto
import com.whatever.caro.study.StudyReportPeriod
import com.whatever.caro.study.StudyReportRatingCountsDto
import com.whatever.caro.study.StudyReportReviewDto
import com.whatever.caro.study.StudyReportSessionDto
import com.whatever.caro.study.StudyReportSessionKey
import com.whatever.caro.study.internal.streak.StudyDayRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class StudyReportService(
    private val studyReportRepository: StudyReportRepository,
    private val studyDayRepository: StudyDayRepository,
) : StudyReportApi {
    override fun getMaxReviewId(
        userId: Long,
    ): Long = studyReportRepository.findMaxReviewId(userId)

    override fun getHistoryDeckIds(
        userId: Long,
    ): Set<Long> = studyReportRepository.findHistoryDeckIds(userId).toSet()

    override fun getFirstStudyDate(
        userId: Long,
        deckId: Long?,
        maxReviewId: Long,
    ): LocalDate? = studyReportRepository.findFirstStudyDate(userId, deckId, maxReviewId)

    override fun getCounts(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod?,
        maxReviewId: Long,
    ): StudyReportCountsDto {
        val row = studyReportRepository.countReviews(
            userId = userId,
            deckId = deckId,
            startDate = period?.startDate,
            endDate = period?.endDate,
            maxReviewId = maxReviewId,
        )
        return StudyReportCountsDto(
            reviewCount = row.reviewCount,
            uniqueCardCount = row.uniqueCardCount,
            studyDayCount = row.studyDayCount,
            ratings = ratingCounts(row.easy, row.fair, row.again),
        )
    }

    override fun getDailyCounts(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod,
        maxReviewId: Long,
    ): List<StudyReportDailyCountDto> =
        studyReportRepository.countReviewsByDate(
            userId = userId,
            deckId = deckId,
            startDate = period.startDate,
            endDate = period.endDate,
            maxReviewId = maxReviewId,
        ).map {
            StudyReportDailyCountDto(
                studyDate = it.studyDate,
                reviewCount = it.reviewCount,
                ratings = ratingCounts(it.easy, it.fair, it.again),
            )
        }

    override fun getRestDates(
        userId: Long,
        period: StudyReportPeriod,
    ): Set<LocalDate> =
        studyDayRepository.findAllByUserIdAndStreakTypeAndStudyDateBetween(
            userId = userId,
            streakType = StreakType.REST_DAY,
            fromDate = period.startDate,
            toDate = period.endDate,
        ).map { it.studyDate }.toSet()

    override fun getSessions(
        userId: Long,
        deckId: Long?,
        period: StudyReportPeriod?,
        after: StudyReportSessionKey?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportSessionDto> =
        studyReportRepository.findSessions(
            userId = userId,
            deckId = deckId,
            startDate = period?.startDate,
            endDate = period?.endDate,
            afterDate = after?.studyDate,
            afterSessionId = after?.sessionId,
            maxReviewId = maxReviewId,
            pageable = PageRequest.of(0, limit),
        ).map {
            StudyReportSessionDto(
                sessionId = it.sessionId,
                deckId = it.deckId,
                studyDate = it.studyDate,
                timezone = it.timezone,
                status = it.status,
                counts = StudyReportCountsDto(
                    reviewCount = it.reviewCount,
                    uniqueCardCount = it.uniqueCardCount,
                    studyDayCount = if (it.reviewCount > 0) 1 else 0,
                    ratings = ratingCounts(it.easy, it.fair, it.again),
                ),
            )
        }

    override fun getCards(
        userId: Long,
        deckId: Long,
        period: StudyReportPeriod?,
        lastRating: Rating?,
        afterReviewId: Long?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportCardDto> {
        val lastReviews = studyReportRepository.findCardLastReviews(
            userId = userId,
            deckId = deckId,
            startDate = period?.startDate,
            endDate = period?.endDate,
            lastRating = lastRating,
            afterReviewId = afterReviewId,
            maxReviewId = maxReviewId,
            pageable = PageRequest.of(0, limit),
        ).takeIf { it.isNotEmpty() } ?: return emptyList()

        val reviewCountByCardId = studyReportRepository.countReviewsByCard(
            userId = userId,
            deckId = deckId,
            cardIds = lastReviews.map { it.cardId },
            startDate = period?.startDate,
            endDate = period?.endDate,
            maxReviewId = maxReviewId,
        ).associate { it.cardId to it.reviewCount }

        return lastReviews.map {
            StudyReportCardDto(
                cardId = it.cardId,
                reviewCount = reviewCountByCardId.getValue(it.cardId),
                lastReviewId = it.reviewId,
                lastRating = it.rating,
                lastReviewedAt = it.reviewedAt,
                lastStudyDate = it.studyDate,
            )
        }
    }

    override fun getCardReviews(
        userId: Long,
        deckId: Long,
        cardId: Long,
        period: StudyReportPeriod?,
        afterReviewId: Long?,
        maxReviewId: Long,
        limit: Int,
    ): List<StudyReportReviewDto> =
        studyReportRepository.findCardReviews(
            userId = userId,
            deckId = deckId,
            cardId = cardId,
            startDate = period?.startDate,
            endDate = period?.endDate,
            afterReviewId = afterReviewId,
            maxReviewId = maxReviewId,
            pageable = PageRequest.of(0, limit),
        ).map {
            StudyReportReviewDto(
                reviewId = it.reviewId,
                sessionId = it.sessionId,
                rating = it.rating,
                studyDate = it.studyDate,
                reviewedAt = it.reviewedAt,
                timezone = it.timezone,
                reviewType = it.reviewType,
            )
        }
}

private fun ratingCounts(
    easy: Long?,
    fair: Long?,
    again: Long?,
): StudyReportRatingCountsDto = StudyReportRatingCountsDto(easy = easy ?: 0, fair = fair ?: 0, again = again ?: 0)
