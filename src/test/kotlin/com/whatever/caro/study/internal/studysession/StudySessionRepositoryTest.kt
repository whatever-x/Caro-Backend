package com.whatever.caro.study.internal.studysession

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class StudySessionRepositoryTest(
    private val studySessionRepository: StudySessionRepository,
) : DescribeSpec({
    val kstZoneId = ZoneId.of("Asia/Seoul")
    val now: Instant = Instant.parse("2026-05-19T01:00:00Z")

    afterEach { studySessionRepository.deleteAllInBatch() }

    fun saveSession(
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        userId: Long = 1L,
        deckId: Long = 1L,
        startedAt: Instant = now,
        dayCutoffHour: Int = 4,
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
            ),
        )

    describe("setStoppedIfActive - 멱등성 가드 (통합 미커버)") {
        it("이미 STOPPED인 row를 재호출하면 effectedRow=0이고 status는 STOPPED로 유지된다") {
            val session = saveSession(status = StudySessionStatus.STOPPED)

            val affected = studySessionRepository.setStoppedIfActive(session.id)

            affected shouldBe 0
            studySessionRepository.findById(session.id).orElseThrow().status shouldBe StudySessionStatus.STOPPED
        }

        it("COMPLETED row를 호출하면 effectedRow=0이고 status는 COMPLETED로 유지된다") {
            val session = saveSession(status = StudySessionStatus.COMPLETED)

            val affected = studySessionRepository.setStoppedIfActive(session.id)

            affected shouldBe 0
            studySessionRepository.findById(session.id).orElseThrow().status shouldBe StudySessionStatus.COMPLETED
        }
    }

    describe("findLatestByUserIdAndDeckIdIn") {
        val yesterday = now.minus(1, ChronoUnit.DAYS)

        it("덱별로 startedAt이 가장 최신인 세션 1건씩만 반환한다") {
            saveSession(deckId = 1L, startedAt = yesterday)
            val latestOfDeck1 = saveSession(deckId = 1L, startedAt = now)
            saveSession(deckId = 2L, startedAt = yesterday)
            val latestOfDeck2 = saveSession(deckId = 2L, startedAt = now)

            val result = studySessionRepository.findLatestByUserIdAndDeckIdIn(
                userId = 1L,
                deckIds = setOf(1L, 2L),
            )

            result.map { it.id }.shouldContainExactlyInAnyOrder(listOf(latestOfDeck1.id, latestOfDeck2.id))
        }

        it("같은 덱에 startedAt이 동일한 세션이 여러 건이면 id가 큰 세션만 반환한다") {
            // uk_session_per_day(user, deck, session_date) 때문에 같은 날짜의 동률은 존재할 수 없어,
            // dayCutoffHour를 다르게 줘서 session_date가 갈리는 startedAt 동률을 재현한다
            saveSession(deckId = 1L, startedAt = now, dayCutoffHour = 11)
            val laterInserted = saveSession(deckId = 1L, startedAt = now, dayCutoffHour = 4)

            val result = studySessionRepository.findLatestByUserIdAndDeckIdIn(
                userId = 1L,
                deckIds = setOf(1L),
            )

            result.map { it.id } shouldBe listOf(laterInserted.id)
        }

        it("다른 userId의 같은 deckId 세션은 더 최신이어도 결과에 영향을 주지 않는다") {
            val mySession = saveSession(userId = 1L, deckId = 1L, startedAt = yesterday)
            saveSession(userId = 2L, deckId = 1L, startedAt = now)

            val result = studySessionRepository.findLatestByUserIdAndDeckIdIn(
                userId = 1L,
                deckIds = setOf(1L),
            )

            result.map { it.id } shouldBe listOf(mySession.id)
        }

        it("deckIds에 포함되지 않은 덱의 세션은 반환되지 않는다") {
            val sessionOfDeck1 = saveSession(deckId = 1L)
            saveSession(deckId = 2L)

            val result = studySessionRepository.findLatestByUserIdAndDeckIdIn(
                userId = 1L,
                deckIds = setOf(1L),
            )

            result.map { it.id } shouldBe listOf(sessionOfDeck1.id)
        }

        it("세션이 없는 덱은 결과에 행 자체가 없다") {
            saveSession(deckId = 1L)

            val result = studySessionRepository.findLatestByUserIdAndDeckIdIn(
                userId = 1L,
                deckIds = setOf(1L, 2L),
            )

            result.map { it.deckId } shouldBe listOf(1L)
        }
    }
})
