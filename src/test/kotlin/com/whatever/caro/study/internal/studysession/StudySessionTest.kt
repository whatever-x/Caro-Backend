package com.whatever.caro.study.internal.studysession

import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class StudySessionTest :
    DescribeSpec({
        val kstZoneId = ZoneId.of("Asia/Seoul")
        val dayCutoffHour = 4
        // 산식: isTodaySession(now) = (sessionDate == now.atZone(KST).minusHours(4).toLocalDate())
        // sessionDate=2026-05-18 기준으로 "now가 cutoff 04:00 직전인지" 경계 검증

        fun createSession(): StudySession =
            StudySession(
                userId = 1L,
                deckId = 1L,
                status = StudySessionStatus.ACTIVE,
                studyType = StudyType.DAILY,
                startedAt = Instant.parse("2026-05-18T00:00:00Z"),
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
                deckPresetIdSnapshot = 1L,
            )

        describe("isTodaySession - KST cutoff=04:00 경계 (sessionDate=2026-05-18 기준)") {
            data class Case(
                val nowKst: String,
                val expected: Boolean,
                val label: String,
            )

            withData(
                nameFn = { it.label },
                // KST 03:59:59 → minusHours(4) = 05-18T23:59:59 → date=05-18 → sessionDate와 같음 → true
                Case(
                    "2026-05-19T03:59:59",
                    true,
                    "KST 2026-05-19 03:59:59 (cutoff 1초 전) → 여전히 전일 sessionDate=2026-05-18 (true)",
                ),
                // KST 04:00:00 → minusHours(4) = 05-19T00:00:00 → date=05-19 → 다른 sessionDate → false
                Case(
                    "2026-05-19T04:00:00",
                    false,
                    "KST 2026-05-19 04:00:00 (cutoff 정각) → 새 sessionDate=2026-05-19로 전환 (false)",
                ),
                // KST 04:00:01 → minusHours(4) = 05-19T00:00:01 → date=05-19 → false
                Case(
                    "2026-05-19T04:00:01",
                    false,
                    "KST 2026-05-19 04:00:01 (cutoff 1초 후) → 새 sessionDate=2026-05-19 (false)",
                ),
                // KST 자정 직후 00:00:01 → minusHours(4) = 05-18T20:00:01 → date=05-18 → true (자정이 분기 아님)
                Case(
                    "2026-05-19T00:00:01",
                    true,
                    "KST 2026-05-19 00:00:01 (자정 직후, cutoff 이전) → 전일 sessionDate=2026-05-18 유지 (true) — 자정이 아닌 cutoff가 분기 기준",
                ),
            ) { case ->
                val now = LocalDateTime.parse(case.nowKst).atZone(kstZoneId).toInstant()
                val session = createSession()

                session.isTodaySession(now) shouldBe case.expected
            }
        }
    })
