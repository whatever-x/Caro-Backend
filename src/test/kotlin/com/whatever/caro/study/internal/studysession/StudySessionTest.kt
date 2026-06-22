package com.whatever.caro.study.internal.studysession

import com.whatever.caro.study.StudySessionStatus
import com.whatever.caro.study.StudyType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
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

        fun createSession(
            newCardsGoal: Int = 0,
            newCardsStudied: Int = 0,
            reviewCardsGoal: Int = 0,
            reviewCardsStudied: Int = 0,
            status: StudySessionStatus = StudySessionStatus.ACTIVE,
        ): StudySession =
            StudySession(
                userId = 1L,
                deckId = 1L,
                status = status,
                studyType = StudyType.DAILY,
                startedAt = Instant.parse("2026-05-18T00:00:00Z"),
                timezone = kstZoneId,
                dayCutoffHour = dayCutoffHour,
                deckPresetIdSnapshot = 1L,
                newCardsGoal = newCardsGoal,
                newCardsStudied = newCardsStudied,
                reviewCardsGoal = reviewCardsGoal,
                reviewCardsStudied = reviewCardsStudied,
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

        describe("recalculateGoals") {
            it("여유분이 부족하면 goal을 studied+available로 감소시키고, 충분하면 기존 goal을 상한으로 유지한다") {
                val newCardsGoal = 10
                val reviewCardsGoal = 10
                val s = createSession(newCardsGoal = newCardsGoal, newCardsStudied = 5, reviewCardsGoal = reviewCardsGoal, reviewCardsStudied = 5)

                val availableReviewGoal = 4
                s.recalculateGoals(availableNewGoal = 100, availableReviewGoal = availableReviewGoal)

                // review: min(10, 5+4) = 9, 여유분 부족하므로 감소
                s.reviewCardsGoal shouldBe s.reviewCardsStudied + availableReviewGoal

                // new: min(10, 5+100) = 10, 여유분 충분하므로 기존 goal 유지
                s.newCardsGoal shouldBe newCardsGoal
            }

            it("available이 0이면 goal이 studied까지 내려간다") {
                val s = createSession(newCardsGoal = 10, newCardsStudied = 4, reviewCardsGoal = 10, reviewCardsStudied = 4)

                s.recalculateGoals(availableNewGoal = 0, availableReviewGoal = 0)

                // min(10, 4+0) = 4
                s.newCardsGoal shouldBe 4
                s.reviewCardsGoal shouldBe 4
            }

            it("studied가 이미 goal을 초과해도 goal은 변하지 않는다") {
                val s = createSession(newCardsGoal = 5, newCardsStudied = 7, reviewCardsGoal = 5, reviewCardsStudied = 7)

                s.recalculateGoals(availableNewGoal = 0, availableReviewGoal = 0)

                // min(5, 7+0) = 5, studied 초과분이 goal을 끌어올리지 않는다
                s.newCardsGoal shouldBe 5
                s.reviewCardsGoal shouldBe 5
            }
        }

        describe("completeIfGoalAchieved") {
            it("new와 review 둘 다 studied가 goal 이상이면 세션을 완료 상태로 전환한다") {
                val now = Instant.now()
                val s = createSession(newCardsGoal = 2, newCardsStudied = 2, reviewCardsGoal = 3, reviewCardsStudied = 3)

                s.completeIfGoalAchieved(now)

                s.status shouldBe StudySessionStatus.COMPLETED
                s.endedAt shouldBe now
            }

            it("review의 studied가 goal 미만이면 세션을 활성 상태를 유지한다") {
                val now = Instant.now()
                // review가 미달
                val s = createSession(newCardsGoal = 2, newCardsStudied = 2, reviewCardsGoal = 3, reviewCardsStudied = 1)

                s.completeIfGoalAchieved(now)

                s.status shouldBe StudySessionStatus.ACTIVE
                s.endedAt.shouldBeNull()
            }

            it("new의 studied가 goal 미만이면 세션을 활성 상태를 유지한다") {
                val now = Instant.now()
                // new가 미달
                val s = createSession(newCardsGoal = 2, newCardsStudied = 1, reviewCardsGoal = 3, reviewCardsStudied = 3)

                s.completeIfGoalAchieved(now)

                s.status shouldBe StudySessionStatus.ACTIVE
                s.endedAt.shouldBeNull()
            }

            it("goal이 0,0이면 세션을 완료 상태로 전환한다.") {
                val now = Instant.now()
                val s = createSession(newCardsGoal = 0, newCardsStudied = 0, reviewCardsGoal = 0, reviewCardsStudied = 0)

                s.completeIfGoalAchieved(now)

                s.status shouldBe StudySessionStatus.COMPLETED
            }

            context("세션 상태에 따라 complete 시 반환값이 달라진다") {
                withData(
                    nameFn = { "세션 상태가 $it 라면 ${it == StudySessionStatus.ACTIVE}를 반환한다" },
                    listOf(StudySessionStatus.ACTIVE, StudySessionStatus.STOPPED, StudySessionStatus.COMPLETED),
                ) { status ->
                    val now = Instant.now()
                    val s = createSession(status = status)

                    val result = s.completeIfGoalAchieved(now)

                    result shouldBe (status == StudySessionStatus.ACTIVE)
                }
            }
        }
    })
