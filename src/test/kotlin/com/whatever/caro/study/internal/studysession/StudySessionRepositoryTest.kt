package com.whatever.caro.study.internal.studysession

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockDeckPresetApiConfig::class)
class StudySessionRepositoryTest(
    private val studySessionRepository: StudySessionRepository,
) : DescribeSpec({
    val kstZoneId = ZoneId.of("Asia/Seoul")
    val dayCutoffHour = 4

    afterTest { studySessionRepository.deleteAllInBatch() }

    fun saveSessionOn(
        sessionDate: LocalDate,
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
        userId: Long = 1L,
        deckId: Long = 1L,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = userId,
                deckId = deckId,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = Instant.parse("${sessionDate}T12:00:00Z"), // cutoff 적용 후에도 sessionDate가 변경되지 않도록 보장
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
                deckPresetIdSnapshot = 1L,
            ),
        )

    describe("findByUserIdAndDeckIdAndSessionDateBetween") {
        it("from~to 범위 안의 sessionDate 세션만 반환한다") {
            val d17 = saveSessionOn(LocalDate.parse("2026-05-17"))
            val d18 = saveSessionOn(LocalDate.parse("2026-05-18"))
            val d19 = saveSessionOn(LocalDate.parse("2026-05-19"))
            saveSessionOn(LocalDate.parse("2026-05-16")) // 범위 밖(과거)
            saveSessionOn(LocalDate.parse("2026-05-20")) // 범위 밖(미래)

            val result = studySessionRepository.findByUserIdAndDeckIdAndSessionDateBetween(
                userId = 1L,
                deckId = 1L,
                fromDate = LocalDate.parse("2026-05-17"),
                toDate = LocalDate.parse("2026-05-19"),
            )

            result.map { it.id }.shouldContainExactlyInAnyOrder(listOf(d17.id, d18.id, d19.id))
        }

        it("다른 user나 deck의 세션은 반환하지 않는다") {
            val mySession = saveSessionOn(LocalDate.parse("2026-05-18"), userId = 1L, deckId = 1L)
            saveSessionOn(LocalDate.parse("2026-05-18"), userId = 2L, deckId = 1L)
            saveSessionOn(LocalDate.parse("2026-05-18"), userId = 1L, deckId = 2L)

            val result = studySessionRepository.findByUserIdAndDeckIdAndSessionDateBetween(
                userId = 1L,
                deckId = 1L,
                fromDate = LocalDate.parse("2026-05-17"),
                toDate = LocalDate.parse("2026-05-19"),
            )

            result.map { it.id } shouldBe listOf(mySession.id)
        }
    }

    describe("findByUserIdAndDeckIdInAndSessionDateBetween") {
        it("여러 deck의 범위 내 세션을 한 번에 조회한다") {
            val session1 = saveSessionOn(sessionDate = LocalDate.parse("2026-05-18"), deckId = 1L)
            val session2 = saveSessionOn(sessionDate = LocalDate.parse("2026-05-19"), deckId = 2L)
            saveSessionOn(LocalDate.parse("2026-05-18"), deckId = 3L) // deckIds 밖
            saveSessionOn(LocalDate.parse("2026-05-16"), deckId = 1L) // 범위 밖

            val result = studySessionRepository.findByUserIdAndDeckIdInAndSessionDateBetween(
                userId = 1L,
                deckIds = setOf(session1.deckId, session2.deckId),
                fromDate = LocalDate.parse("2026-05-17"),
                toDate = LocalDate.parse("2026-05-19"),
            )

            result.map { it.id }.shouldContainExactlyInAnyOrder(listOf(session1.id, session2.id))
        }
    }

    describe("stopStaledActiveBefore") {
        it("before 이하인 sessionDate의 ACTIVE 세션만 STOPPED로 바꾼다") {
            val before = LocalDate.parse("2026-05-16")

            val staled = saveSessionOn(sessionDate = before.minusDays(1), status = StudySessionStatus.ACTIVE)
            val boundary = saveSessionOn(sessionDate = before, status = StudySessionStatus.ACTIVE)
            val future = saveSessionOn(sessionDate = before.plusDays(1), status = StudySessionStatus.ACTIVE)

            val affectedRow = studySessionRepository.stopStaledActiveBefore(before = before)

            affectedRow shouldBe 2
            studySessionRepository.findById(staled.id).orElseThrow().status shouldBe StudySessionStatus.STOPPED
            studySessionRepository.findById(boundary.id).orElseThrow().status shouldBe StudySessionStatus.STOPPED
            studySessionRepository.findById(future.id).orElseThrow().status shouldBe StudySessionStatus.ACTIVE
        }

        it("이미 종료된(COMPLETED/STOPPED) 세션은 before 이전이어도 변경하지 않는다") {
            val before = LocalDate.parse("2026-05-16")
            val completed = saveSessionOn(sessionDate = LocalDate.parse("2026-05-10"), status = StudySessionStatus.COMPLETED, deckId = 1L)
            val stopped = saveSessionOn(sessionDate = LocalDate.parse("2026-05-10"), status = StudySessionStatus.STOPPED, deckId = 2L)

            val affected = studySessionRepository.stopStaledActiveBefore(before = before)

            affected shouldBe 0
            studySessionRepository.findById(completed.id).orElseThrow().status shouldBe StudySessionStatus.COMPLETED
            studySessionRepository.findById(stopped.id).orElseThrow().status shouldBe StudySessionStatus.STOPPED
        }
    }
})
