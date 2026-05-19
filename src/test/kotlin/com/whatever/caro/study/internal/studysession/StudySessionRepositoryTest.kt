package com.whatever.caro.study.internal.studysession

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class StudySessionRepositoryTest(
    private val studySessionRepository: StudySessionRepository,
) : DescribeSpec({
    val kstZoneId = ZoneId.of("Asia/Seoul")
    val now: Instant = Instant.parse("2026-05-19T01:00:00Z")
    val today: LocalDate = LocalDate.parse("2026-05-19")

    afterEach { studySessionRepository.deleteAllInBatch() }

    fun saveSession(
        status: StudySessionStatus = StudySessionStatus.ACTIVE,
    ): StudySession =
        studySessionRepository.save(
            StudySession(
                userId = 1L,
                deckId = 1L,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = now,
                timezone = kstZoneId,
                dayCutoffHour = 4,
                sessionDate = today,
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
})
