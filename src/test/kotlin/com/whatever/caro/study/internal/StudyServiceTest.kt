package com.whatever.caro.study.internal

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.api.deck.DeckPresetApi
import com.whatever.caro.study.CardLearningStatus
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.TodayStudySessionState
import com.whatever.caro.study.exception.SessionExpiredException
import com.whatever.caro.study.exception.SessionNotFoundException
import com.whatever.caro.study.internal.cardlearningstate.CardLearningState
import com.whatever.caro.study.internal.cardlearningstate.CardLearningStateRepository
import com.whatever.caro.study.internal.studysession.StudySession
import com.whatever.caro.study.internal.studysession.StudySessionRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class StudyServiceTest(
    private val studyService: StudyService,
    private val studySessionRepository: StudySessionRepository,
    private val cardLearningStateRepository: CardLearningStateRepository,
    private val deckPresetApi: DeckPresetApi,
) : DescribeSpec({

    val kstZoneId = ZoneId.of("Asia/Seoul")
    val dayCutoffHour = 4

    val baseNow: Instant = LocalDateTime.parse("2026-05-19T10:00:00").atZone(kstZoneId).toInstant()
    val baseDate: LocalDate = LocalDate.parse("2026-05-19")
    val yesterday: Instant = baseNow.minus(1, ChronoUnit.DAYS)

    afterTest {
        studySessionRepository.deleteAllInBatch()
        cardLearningStateRepository.deleteAllInBatch()
        clearMocks(deckPresetApi)
    }

    fun createSession(
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        startedAt: Instant = baseNow,
        newStudied: Int = 0,
        reviewStudied: Int = 0,
        newCardsGoal: Int = 10,
        reviewCardsGoal: Int = 10,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = userId,
                deckId = deckId,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = startedAt,
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
                deckPresetIdSnapshot = 1L,
                newCardsStudied = newStudied,
                reviewCardsStudied = reviewStudied,
                newCardsGoal = newCardsGoal,
                reviewCardsGoal = reviewCardsGoal,
            ),
        )

    fun createCls(
        cardId: Long,
        userId: Long = USER_ID,
        deckId: Long = DECK_ID,
        status: CardLearningStatus = CardLearningStatus.REVIEW,
        totalReviews: Int = 0,
        consecutiveAgainCount: Int = 0,
        nextReviewDate: LocalDate? = null,
        lastReviewedDate: LocalDate? = null,
    ): CardLearningState =
        cardLearningStateRepository.save(
            CardLearningState(
                cardId = cardId,
                deckId = deckId,
                userId = userId,
                status = status,
                totalReviews = totalReviews,
                consecutiveAgainCount = consecutiveAgainCount,
                nextReviewDate = nextReviewDate,
                lastReviewedDate = lastReviewedDate,
            ),
        )

    describe("getTodaySummary") {

        it("세션이 없고 학습 대상 카드가 있으면 NotStarted를 반환한다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 5, reviewPerDay = 0)
            repeat(3) { i ->
                createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
            }

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            val notStarted = result.shouldBeInstanceOf<TodayStudySessionState.NotStarted>()
            notStarted.pool.newCount shouldBe 3
            notStarted.presetId shouldBe Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.id
        }

        it("세션이 없고 학습 대상 카드가 없으면 RestDay를 반환한다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0)

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result shouldBe TodayStudySessionState.RestDay
        }

        it("ACTIVE 상태인 오늘 세션이면 InProgress를 반환한다") {
            val session = createSession(
                status = StudySessionStatus.ACTIVE,
                newStudied = 3,
                reviewStudied = 5,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()
            inProgress.session.sessionId shouldBe session.id
            inProgress.session.newCardsStudied shouldBe 3
            inProgress.session.reviewCardsStudied shouldBe 5
            inProgress.session.newCardsGoal shouldBe 10
            inProgress.session.reviewCardsGoal shouldBe 10
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("오늘이 아닌 ACTIVE 세션은 오늘 세션으로 취급하지 않고 status를 변경하지 않는다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0)
            val staleSession = createSession(
                status = StudySessionStatus.ACTIVE,
                startedAt = yesterday,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result shouldBe TodayStudySessionState.RestDay

            // getTodaySummary는 readOnly — stale 세션의 status를 변경하지 않는다 (정리는 startOrResume에서만)
            val unchanged = studySessionRepository.findByIdOrNull(staleSession.id)
            unchanged.shouldNotBeNull()
            unchanged.status shouldBe StudySessionStatus.ACTIVE
        }

        it("COMPLETED 상태인 오늘 세션이면 Completed를 반환한다") {
            val session = createSession(
                status = StudySessionStatus.COMPLETED,
                startedAt = baseNow,
                newStudied = 10,
                reviewStudied = 10,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            val completed = result.shouldBeInstanceOf<TodayStudySessionState.Completed>()
            completed.session.sessionId shouldBe session.id
            completed.session.newCardsStudied shouldBe 10
            completed.session.reviewCardsStudied shouldBe 10
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("오늘이 아닌 COMPLETED 세션이면 RestDay를 반환하고 status는 변경되지 않는다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0)
            val session = createSession(
                status = StudySessionStatus.COMPLETED,
                startedAt = yesterday,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result shouldBe TodayStudySessionState.RestDay

            val unchanged = studySessionRepository.findByIdOrNull(session.id)
            unchanged.shouldNotBeNull()
            unchanged.status shouldBe StudySessionStatus.COMPLETED
        }

        it("STOPPED 상태인 오늘 세션은 종료상태로 보아 Completed를 반환한다") {
            // 정상 flow에서는 나올 수 없음
            val session = createSession(
                status = StudySessionStatus.STOPPED,
                startedAt = baseNow,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            val completed = result.shouldBeInstanceOf<TodayStudySessionState.Completed>()
            completed.session.sessionId shouldBe session.id

            val unchanged = studySessionRepository.findByIdOrNull(session.id)
            unchanged.shouldNotBeNull()
            unchanged.status shouldBe StudySessionStatus.STOPPED
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("여러 세션이 있다면 가장 최신 세션을 기준으로 응답한다") {
            // 어제 세션
            createSession(
                status = StudySessionStatus.COMPLETED,
                startedAt = yesterday,
            )
            // 오늘 세션
            val latestSession = createSession(
                status = StudySessionStatus.ACTIVE,
                startedAt = baseNow,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()
            inProgress.session.sessionId shouldBe latestSession.id
        }

        it("다른 유저나, 덱에 속한 세션은 결과에 영향을 주지 않는다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0)
            createSession(
                // 다른 user의 오늘 ACTIVE 세션
                userId = 2L,
                deckId = DECK_ID,
                status = StudySessionStatus.ACTIVE,
                startedAt = baseNow,
            )
            createSession(
                // 다른 deck의 오늘 ACTIVE 세션
                userId = USER_ID,
                deckId = 2L,
                status = StudySessionStatus.ACTIVE,
                startedAt = baseNow,
            )

            val result = studyService.getTodaySummary(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckId = DECK_ID,
            )

            result shouldBe TodayStudySessionState.RestDay
        }
    }

    describe("getTodaySummaries") {

        it("ACTIVE 상태인 오늘 세션이 있는 덱은 InProgress로 반환된다") {
            val session = createSession(
                status = StudySessionStatus.ACTIVE,
                newStudied = 3,
                reviewStudied = 5,
            )

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val inProgress = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.InProgress>()
            inProgress.session.sessionId shouldBe session.id
            inProgress.session.newCardsStudied shouldBe 3
            inProgress.session.reviewCardsStudied shouldBe 5
            inProgress.session.estimatedTotal shouldBe (session.newCardsGoal + session.reviewCardsGoal)
        }

        it("COMPLETED 상태인 오늘 세션이 있는 덱은 Completed로 반환된다") {
            val session = createSession(
                status = StudySessionStatus.COMPLETED,
                newStudied = 10,
                reviewStudied = 10,
            )

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val completed = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.Completed>()
            completed.session.sessionId shouldBe session.id
        }

        it("어제의 ACTIVE 세션은 무시하고 pool 경로로 판정한다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(DECK_ID to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 5, reviewPerDay = 0))
            createSession(status = StudySessionStatus.ACTIVE, startedAt = yesterday)
            repeat(2) { i ->
                createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
            }

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val notStarted = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.NotStarted>()
            notStarted.pool.newCount shouldBe 2
        }

        it("STOPPED 상태인 오늘 세션은 종료상태로 보아 Completed로 반환된다") {
            // 정상 flow에서는 나올 수 없음
            val session = createSession(status = StudySessionStatus.STOPPED, startedAt = baseNow)

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val completed = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.Completed>()
            completed.session.sessionId shouldBe session.id
        }

        it("세션이 없고 학습 대상 카드가 있으면 정확한 pool 값의 NotStarted를 반환한다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(DECK_ID to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10))
            repeat(3) { i ->
                createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
            }
            repeat(2) { i ->
                createCls(
                    cardId = (100 + i).toLong(),
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = baseDate,
                )
            }

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val notStarted = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.NotStarted>()
            notStarted.pool.newCount shouldBe 3
            notStarted.pool.reviewCount shouldBe 2
            notStarted.presetId shouldBe Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.id
        }

        it("세션이 없고 학습 대상 카드도 없으면 RestDay를 반환한다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(DECK_ID to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10))

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            result[DECK_ID] shouldBe TodayStudySessionState.RestDay
        }

        it("학습 대상 카드 수가 preset의 perDay를 넘으면 perDay까지만 pool에 담긴다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(DECK_ID to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 2, reviewPerDay = 1))
            repeat(5) { i ->
                createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
            }
            repeat(3) { i ->
                createCls(
                    cardId = (100 + i).toLong(),
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = baseDate,
                )
            }

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(DECK_ID),
            )

            val notStarted = result[DECK_ID].shouldBeInstanceOf<TodayStudySessionState.NotStarted>()
            notStarted.pool.newCount shouldBe 2
            notStarted.pool.reviewCount shouldBe 1
        }

        it("여러 덱의 상태가 혼합되어도 입력한 모든 deckId가 결과 키로 반환된다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(
                    3L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10),
                    4L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10),
                )
            createSession(deckId = 1L, status = StudySessionStatus.ACTIVE)
            createSession(deckId = 2L, status = StudySessionStatus.COMPLETED)
            repeat(3) { i ->
                createCls(cardId = (i + 1).toLong(), deckId = 3L, status = CardLearningStatus.NEW)
            }
            // deckId=4L는 세션도 카드도 없음

            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(1L, 2L, 3L, 4L),
            )

            result.keys shouldBe setOf(1L, 2L, 3L, 4L)
            result[1L].shouldBeInstanceOf<TodayStudySessionState.InProgress>()
            result[2L].shouldBeInstanceOf<TodayStudySessionState.Completed>()
            result[3L].shouldBeInstanceOf<TodayStudySessionState.NotStarted>()
            result[4L] shouldBe TodayStudySessionState.RestDay
        }

        it("모든 덱에 오늘 세션이 있으면 preset 배치 조회를 호출하지 않는다") {
            createSession(deckId = 1L, status = StudySessionStatus.ACTIVE)
            createSession(deckId = 2L, status = StudySessionStatus.COMPLETED)

            studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(1L, 2L),
            )

            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) }
        }

        it("세션이 없는 덱이 있어도 단건 preset 조회는 호출하지 않는다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(2L to Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0))
            createSession(deckId = 1L, status = StudySessionStatus.ACTIVE)

            studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(1L, 2L),
            )

            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("같은 데이터에 대해 단건 getTodaySummary와 동일한 결과를 반환한다") {
            val preset = Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 10, reviewPerDay = 10)
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns preset
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns
                mapOf(2L to preset, 3L to preset)
            createSession(deckId = 1L, status = StudySessionStatus.ACTIVE, newStudied = 3) // InProgress
            repeat(3) { i ->
                createCls(cardId = (i + 1).toLong(), deckId = 2L, status = CardLearningStatus.NEW) // NotStarted
            }
            // deckId=3L는 세션도 카드도 없음 → RestDay

            val batchResult = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = setOf(1L, 2L, 3L),
            )

            listOf(1L, 2L, 3L).forEach { deckId ->
                batchResult[deckId] shouldBe studyService.getTodaySummary(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    deckId = deckId,
                )
            }
        }

        it("세션이 없는 덱에 preset이 연결되어 있지 않으면 IllegalStateException을 던진다") {
            every { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) } returns emptyMap()

            shouldThrow<IllegalStateException> {
                studyService.getTodaySummaries(
                    now = baseNow,
                    timezone = kstZoneId,
                    userId = USER_ID,
                    deckIds = setOf(DECK_ID),
                )
            }
        }

        it("deckIds가 비어있으면 외부 호출 없이 빈 맵을 반환한다") {
            val result = studyService.getTodaySummaries(
                now = baseNow,
                timezone = kstZoneId,
                userId = USER_ID,
                deckIds = emptySet(),
            )

            result.shouldBeEmpty()
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetsByDeckId(any(), any()) }
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }
    }

    describe("getLearningStates") {

        it("매칭되는 카드는 cardId 기준 맵으로 매핑되고, 매칭되지 않는 요소는 무시한다") {
            val states = listOf(
                createCls(
                    cardId = 1L,
                    totalReviews = 3,
                    consecutiveAgainCount = 1,
                    lastReviewedDate = baseDate,
                ),
                createCls(
                    cardId = 2L,
                    totalReviews = 0,
                    consecutiveAgainCount = 0,
                ),
            )

            val cardIds = states.map { it.cardId }
            val result = studyService.getLearningStates(
                userId = USER_ID,
                cardIds = cardIds + 3L,
            )

            result.keys.shouldContainExactlyInAnyOrder(cardIds)

            val firstId = cardIds.first()
            result[firstId]?.totalReviews shouldBe 3
            result[firstId]?.consecutiveAgainCount shouldBe 1
            result[firstId]?.lastReviewedDate shouldBe baseDate

            val secondId = cardIds.last()
            result[secondId]?.totalReviews shouldBe 0
        }

        it("내 카드 학습 상태만 조회할 수 있다") {
            val states = listOf(
                createCls(
                    cardId = 1L,
                    userId = USER_ID,
                ),
                createCls(
                    cardId = 2L,
                    userId = 2L,
                ),
            )

            val result = studyService.getLearningStates(
                userId = states.first().userId,
                cardIds = states.map { it.cardId },
            )

            result.keys.shouldContainExactlyInAnyOrder(setOf(states.first().cardId))
        }

        it("조회할 card Id가 비었다면 empty map을 반환한다") {
            createCls(
                cardId = 1L,
            )

            val result = studyService.getLearningStates(
                userId = USER_ID,
                cardIds = emptyList(),
            )

            result.shouldBeEmpty()
        }
    }

    describe("startOrResumeDailyStudySession") {
        context("오늘자 세션이 없을 때") {
            it("처음 세션을 생성하는 경우 ACTIVE 세션이 생성된다") {
                val newCardGoal = 5
                val reviewCardGoal = 3
                every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                    Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = newCardGoal, reviewPerDay = reviewCardGoal)

                repeat(10) { i ->
                    createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
                }
                repeat(5) { i ->
                    createCls(
                        cardId = (100 + i).toLong(),
                        status = CardLearningStatus.REVIEW,
                        nextReviewDate = baseDate,
                    )
                }

                val result = studyService.startOrResumeDailyStudySession(
                    now = baseNow,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()
                inProgress.session.status shouldBe StudySessionStatus.ACTIVE
                inProgress.session.newCardsGoal shouldBe newCardGoal
                inProgress.session.reviewCardsGoal shouldBe reviewCardGoal

                val saved = studySessionRepository.findByIdOrNull(inProgress.session.sessionId)
                saved.shouldNotBeNull()
                saved.status shouldBe StudySessionStatus.ACTIVE
                saved.newCardsGoal shouldBe newCardGoal
                saved.reviewCardsGoal shouldBe reviewCardGoal
                saved.deckPresetIdSnapshot shouldBe Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.id
            }

            it("과거 세션이 ACTIVE로 남아있을 경우 정리하지 않고 신규 ACTIVE 세션을 생성한다") {
                every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                    Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE
                repeat(5) { i ->
                    createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
                }
                val staleSession = createSession(status = StudySessionStatus.ACTIVE, startedAt = yesterday)

                val result = studyService.startOrResumeDailyStudySession(
                    now = baseNow,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()

                val sessions = studySessionRepository.findAll()
                sessions.size shouldBe 2

                val unchangedStaleSession = sessions.find { it.id == staleSession.id }
                unchangedStaleSession.shouldNotBeNull()
                unchangedStaleSession.status shouldBe StudySessionStatus.ACTIVE // 여전히 ACTIVE

                val todaySession = sessions.find { it.id == inProgress.session.sessionId }
                todaySession.shouldNotBeNull()
                todaySession.status shouldBe StudySessionStatus.ACTIVE
            }
        }

        context("과거 세션이 종료된 상태일 때") {
            withData(
                nameFn = { "과거 세션이 $it 상태라면 신규 ACTIVE 세션을 생성한다" },
                StudySessionStatus.COMPLETED,
                StudySessionStatus.STOPPED,
            ) { pastStatus ->
                every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                    Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE
                repeat(5) { i ->
                    createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
                }
                createSession(status = pastStatus, startedAt = yesterday)

                val result = studyService.startOrResumeDailyStudySession(
                    now = baseNow,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()

                val sessions = studySessionRepository.findAll()
                sessions.size shouldBe 2

                val todaySession = sessions.find { it.id == inProgress.session.sessionId }
                todaySession.shouldNotBeNull()
                todaySession.status shouldBe StudySessionStatus.ACTIVE
            }
        }

        context("단조적이지 않은 시간으로 조회") {
            it("미래 날짜에 세션을 만든 뒤 과거(오늘)로 돌아와도 오늘 세션을 재개한다") {
                every {
                    deckPresetApi.getLatestDeckPresetByUser(any(), any())
                } returns Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 5, reviewPerDay = 0)
                repeat(5) { i ->
                    createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
                }
                val today = baseNow
                val future = baseNow.plus(1, ChronoUnit.DAYS)

                val todayResult = studyService.startOrResumeDailyStudySession(
                    now = today,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )
                val todaySessionId = todayResult.shouldBeInstanceOf<TodayStudySessionState.InProgress>().session.sessionId

                // 시간대 동쪽으로 이동(미래 날짜) -> 새 세션 생성
                studyService.startOrResumeDailyStudySession(
                    now = future,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                // 시간대 서쪽으로 이동(오늘로 복귀) -> 세션 재개
                val resumed = studyService.startOrResumeDailyStudySession(
                    now = today,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                resumed.shouldBeInstanceOf<TodayStudySessionState.InProgress>().session.sessionId shouldBe todaySessionId
                // 미래 세션은 STOP되지 않고 그대로 남아 날짜별 2개 세션이 존재한다
                studySessionRepository.findAll().size shouldBe 2
            }
        }

        context("학습 목표 동기화") {
            it("재개 시 학습 카드 풀이 줄어 이미 목표를 채웠다면 COMPLETED로 전환된다") {
                val session = createSession(
                    status = StudySessionStatus.ACTIVE,
                    newStudied = 3,
                    reviewStudied = 0,
                    newCardsGoal = 10,
                    reviewCardsGoal = 0,
                )

                val result = studyService.startOrResumeDailyStudySession(
                    now = baseNow,
                    userId = USER_ID,
                    deckId = DECK_ID,
                    studyType = StudyType.DAILY,
                    timezone = kstZoneId,
                    dayCutoffHour = dayCutoffHour,
                )

                val completed = result.shouldBeInstanceOf<TodayStudySessionState.Completed>()
                completed.session.sessionId shouldBe session.id
                completed.session.newCardsGoal shouldBe 3

                val saved = studySessionRepository.findByIdOrNull(session.id)
                saved.shouldNotBeNull()
                saved.status shouldBe StudySessionStatus.COMPLETED
            }
        }

        it("오늘 세션이 ACTIVE 상태라면 신규 생성 없이 기존 세션을 그대로 반환한다") {
            val existingSession = createSession(status = StudySessionStatus.ACTIVE)
            repeat(10) { i ->
                createCls(cardId = (i + 1).toLong(), status = CardLearningStatus.NEW)
            }

            val result = studyService.startOrResumeDailyStudySession(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
                studyType = StudyType.DAILY,
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
            )

            val inProgress = result.shouldBeInstanceOf<TodayStudySessionState.InProgress>()
            inProgress.session.sessionId shouldBe existingSession.id
            studySessionRepository.findAll().size shouldBe 1
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("오늘 세션이 COMPLETED 상태라면 신규 생성 없이 기존 세션을 그대로 반환한다") {
            val existing = createSession(status = StudySessionStatus.COMPLETED)

            val result = studyService.startOrResumeDailyStudySession(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
                studyType = StudyType.DAILY,
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
            )

            val completed = result.shouldBeInstanceOf<TodayStudySessionState.Completed>()
            completed.session.sessionId shouldBe existing.id
            completed.session.status shouldBe StudySessionStatus.COMPLETED
            studySessionRepository.findAll().size shouldBe 1
            verify(exactly = 0) { deckPresetApi.getLatestDeckPresetByUser(any(), any()) }
        }

        it("학습 대상이 없으면 세션을 생성하지 않고 RestDay를 반환한다") {
            every { deckPresetApi.getLatestDeckPresetByUser(any(), any()) } returns
                Sm2ParamsFixture.DECK_PRESET_DTO_FIXTURE.copy(newPerDay = 0, reviewPerDay = 0)

            val result = studyService.startOrResumeDailyStudySession(
                now = baseNow,
                userId = USER_ID,
                deckId = DECK_ID,
                studyType = StudyType.DAILY,
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
            )

            result shouldBe TodayStudySessionState.RestDay
            studySessionRepository.findAll().size shouldBe 0
        }
    }

    describe("getStudySessionCardQueue") {

        it("학습 대상 카드 목록 조회 시 newPoolSize/reviewPoolSize 만큼 카드가 반환된다") {
            val session = createSession(
                newCardsGoal = 3,
                newStudied = 1,
                reviewCardsGoal = 5,
                reviewStudied = 2,
            )
            val beforeSessionDate = baseDate.minusDays(1L)
            repeat(10) { i ->
                createCls(
                    cardId = (i + 1).toLong(),
                    status = CardLearningStatus.NEW,
                    lastReviewedDate = beforeSessionDate,
                )
                createCls(
                    cardId = (10 + i + 1).toLong(),
                    status = CardLearningStatus.REVIEW,
                    nextReviewDate = baseDate,
                    lastReviewedDate = beforeSessionDate,
                )
            }

            val result = studyService.getStudySessionCardQueue(
                userId = USER_ID,
                sessionId = session.id,
                now = baseNow,
                timezone = kstZoneId,
            )

            result.filter { it.status == CardLearningStatus.NEW }.size shouldBe (session.newCardsGoal - session.newCardsStudied)
            result.filter { it.status == CardLearningStatus.REVIEW }.size shouldBe (session.reviewCardsGoal - session.reviewCardsStudied)
        }

        it("존재하지 않는 sessionId면 SessionNotFoundException을 던진다") {
            shouldThrow<SessionNotFoundException> {
                studyService.getStudySessionCardQueue(
                    userId = USER_ID,
                    sessionId = 999L,
                    now = baseNow,
                    timezone = kstZoneId,
                )
            }
        }

        it("다른 user의 session 조회 시 SessionNotFoundException을 던진다") {
            val session = createSession(userId = 2L)

            shouldThrow<SessionNotFoundException> {
                studyService.getStudySessionCardQueue(
                    userId = USER_ID,
                    sessionId = session.id,
                    now = baseNow,
                    timezone = kstZoneId,
                )
            }
        }

        withData(
            nameFn = { "오늘 세션이 아니고 $it 상태라면 SessionExpiredException을 던진다" },
            StudySessionStatus.ACTIVE,
            StudySessionStatus.COMPLETED,
            StudySessionStatus.STOPPED,
        ) { sessionState ->
            val session = createSession(status = sessionState, startedAt = yesterday)

            shouldThrow<SessionExpiredException> {
                studyService.getStudySessionCardQueue(
                    userId = USER_ID,
                    sessionId = session.id,
                    now = baseNow,
                    timezone = kstZoneId,
                )
            }
        }

        it("NEW 카드를 모두 학습했다면 newQueue가 비어있다") {
            val session = createSession(newCardsGoal = 3, newStudied = 3)

            val result = studyService.getStudySessionCardQueue(
                userId = USER_ID,
                sessionId = session.id,
                now = baseNow,
                timezone = kstZoneId,
            )

            result.filter { it.status == CardLearningStatus.NEW }.size shouldBe 0
        }

        it("REVIEW 카드를 모두 학습했다면 reviewQueue가 비어있다") {
            val session = createSession(reviewCardsGoal = 3, reviewStudied = 3)

            val result = studyService.getStudySessionCardQueue(
                userId = USER_ID,
                sessionId = session.id,
                now = baseNow,
                timezone = kstZoneId,
            )

            result.filter { it.status == CardLearningStatus.REVIEW }.size shouldBe 0
        }

        it("세션 진입 이후 평가된 카드는 같은 세션에서 다시 나오지 않는다") {
            val session = createSession()
            createCls(
                cardId = 1L,
                status = CardLearningStatus.REVIEW,
                nextReviewDate = baseDate.plusDays(1L),
                lastReviewedDate = baseDate, // 오늘(sessionDate)에 평가됨
            )
            createCls(
                cardId = 2L,
                status = CardLearningStatus.NEW,
                lastReviewedDate = baseDate, // 오늘(sessionDate)에 평가됨
            )

            val result = studyService.getStudySessionCardQueue(
                userId = USER_ID,
                sessionId = session.id,
                now = baseNow,
                timezone = kstZoneId,
            )

            result.filter { it.status == CardLearningStatus.NEW }.size shouldBe 0
            result.filter { it.status == CardLearningStatus.REVIEW }.size shouldBe 0
        }

        it("REVIEW queue는 nextReviewDate 기준 오름차순으로 정렬된다") {
            val session = createSession()
            val justBeforeSessionDate = baseDate.minusDays(1L)
            val thirdReview = createCls(
                cardId = 1L,
                status = CardLearningStatus.REVIEW,
                nextReviewDate = baseDate.minusDays(1L),
                lastReviewedDate = justBeforeSessionDate,
            )
            val firstReview = createCls(
                cardId = 2L,
                status = CardLearningStatus.REVIEW,
                nextReviewDate = baseDate.minusDays(3L),
                lastReviewedDate = justBeforeSessionDate,
            )
            val secondReview = createCls(
                cardId = 3L,
                status = CardLearningStatus.REVIEW,
                nextReviewDate = baseDate.minusDays(2L),
                lastReviewedDate = justBeforeSessionDate,
            )

            val result = studyService.getStudySessionCardQueue(
                userId = USER_ID,
                sessionId = session.id,
                now = baseNow,
                timezone = kstZoneId,
            )

            val orderedCardId = listOf(
                firstReview.cardId,
                secondReview.cardId,
                thirdReview.cardId,
            )
            result
                .filter { it.status == CardLearningStatus.REVIEW }
                .map { it.cardId } shouldContainExactly orderedCardId
        }

        context("세션을 시작한 tz과 클라이언트의 tz가 다를 경우 클라이언트 기준으로 판단한다") {
            val southZoneId = ZoneId.of("America/Los_Angeles") // 서쪽 (UTC-7)
            val eastZoneId = ZoneId.of("Pacific/Auckland") // 동쪽 (UTC+12)

            it("시작한 tz 기준으로는 만료지만 클라이언트 tz는 만료가 아니라면 큐를 반환한다") {
                val session = createSession()
                val result = studyService.getStudySessionCardQueue(
                    userId = USER_ID,
                    sessionId = session.id,
                    now = Instant.parse("2026-05-20T00:00:00Z"), // 시작한 kst 기준으로는 만료, 서쪽에 있는 시간대 기준으로는 05-19으로 만료가 아님
                    timezone = southZoneId,
                )
                result shouldBe emptyList()
            }

            it("시작한 tz 기준으로는 만료가 아니지만, 클라이언트 tz는 만료라면 SessionExpiredException을 던진다") {
                val session = createSession()
                shouldThrow<SessionExpiredException> {
                    studyService.getStudySessionCardQueue(
                        userId = USER_ID,
                        sessionId = session.id,
                        now = Instant.parse("2026-05-19T17:00:00Z"), // 시작한 kst 기준으로는 만료가 아니지만, 더 동쪽에 있는 시간대 기준으로는 50-20으로 만료
                        timezone = eastZoneId,
                    )
                }
            }
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val DECK_ID = 1L
    }
}
