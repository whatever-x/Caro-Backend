package com.whatever.caro.study.internal.streak

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.study.StreakType
import com.whatever.caro.study.internal.MockDeckPresetApiConfig
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.verify
import org.springframework.context.annotation.Import
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class, MockDeckPresetApiConfig::class)
class StreakServiceTest(
    private val streakService: StreakService,
    private val streakStateRepository: StreakStateRepository,
    private val studyDayRepository: StudyDayRepository,
    private val restDayCheckService: RestDayCheckService,
) : DescribeSpec({

    val d15 = LocalDate.of(2026, 6, 15)
    val d16 = LocalDate.of(2026, 6, 16)
    val d17 = LocalDate.of(2026, 6, 17)
    val d18 = LocalDate.of(2026, 6, 18)
    val d19 = LocalDate.of(2026, 6, 19)

    val kst = ZoneId.of("Asia/Seoul")
    fun instantOn(
        date: LocalDate,
        hour: Int = 12,
    ): Instant = date.atTime(hour, 0).atZone(kst).toInstant()

    afterTest {
        clearMocks(restDayCheckService)
        streakStateRepository.deleteAllInBatch()
        studyDayRepository.deleteAllInBatch()
    }

    describe("recordStudied") {
        it("최초 학습이면 streak이 1로 생성되고 학습일이 DAILY_STUDY로 기록된다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d15

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.first().streakType shouldBe StreakType.DAILY_STUDY
        }

        it("연속 3일을 학습하면 streak이 3이 된다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)
            streakService.recordStudied(userId = USER_ID, studyDate = d17)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 3
            streakState.lastRecordedDate shouldBe d17
        }

        it("중간에 학습하지 않은 날이 있으면 streak이 끊겨 1이 된다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            // d16 공백
            streakService.recordStudied(userId = USER_ID, studyDate = d17)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d17
            studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID) shouldHaveSize 2
        }

        it("streak이 쌓였더라도 한번 끊겼다면 이전 streak이 누적되지 않고 1부터 다시 센다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)
            streakService.recordStudied(userId = USER_ID, studyDate = d17)
            // d18 공백
            streakService.recordStudied(userId = USER_ID, studyDate = d19)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d19
        }

        it("하루에 여러 덱을 학습해도 학습일은 하루로만 계산된다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d17)
            streakService.recordStudied(userId = USER_ID, studyDate = d17) // 다른 덱을 학습해 중복 트리거

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d17

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.first().streakType shouldBe StreakType.DAILY_STUDY
        }

        it("같은 날에 학습 기록이 재전송되어도 streak이 중복 증가하지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)
            streakService.recordStudied(userId = USER_ID, studyDate = d16) // 다른 덱을 학습해 중복 트리거

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 2

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 2
        }

        it("휴식일로 기록되었을 때 학습 기록이 들어온다면 학습일로 수정되고 streak에 반영된다") {
            streakService.recordRest(userId = USER_ID, restDate = d17)
            streakService.recordStudied(userId = USER_ID, studyDate = d17)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.first().streakType shouldBe StreakType.DAILY_STUDY
        }

        it("한 사용자의 학습 기록은 다른 사용자의 streak에 영향을 주지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)
            streakService.recordStudied(userId = USER_ID, studyDate = d17)

            streakService.recordStudied(userId = OTHER_USER_ID, studyDate = d18)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 3

            val otherStreakState = streakStateRepository.findByUserId(userId = OTHER_USER_ID)!!
            otherStreakState.currentStreak shouldBe 1
        }
    }

    describe("recordRest") {
        it("휴식일은 streak 카운트에 포함되지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordRest(userId = USER_ID, restDate = d16)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d16

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 2
            studyDays.first().streakType shouldBe StreakType.REST_DAY
        }

        it("휴식일은 streak을 이어준다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15) // 학습일
            streakService.recordRest(userId = USER_ID, restDate = d16)
            streakService.recordStudied(userId = USER_ID, studyDate = d17) // 학습일
            streakService.recordRest(userId = USER_ID, restDate = d18)
            streakService.recordRest(userId = USER_ID, restDate = d19)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 2
            streakState.lastRecordedDate shouldBe d19
        }

        it("학습일로 기록되었다면 휴식 기록이 들어와도 덮어쓰지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d17)
            streakService.recordRest(userId = USER_ID, restDate = d17)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.first().streakType shouldBe StreakType.DAILY_STUDY
        }

        it("같은 날에 휴식일이 중복 전송되어도 상태가 변하지 않는다") {
            streakService.recordRest(userId = USER_ID, restDate = d17)
            streakService.recordRest(userId = USER_ID, restDate = d17)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 0
            streakState.lastRecordedDate shouldBe d17

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.first().streakType shouldBe StreakType.REST_DAY
        }

        it("이미 끊긴 streak은 휴식일로 이어질 수 없다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            // d16 공백
            streakService.recordRest(userId = USER_ID, restDate = d17) // 휴식일
            streakService.recordStudied(userId = USER_ID, studyDate = d18)

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 1
            streakState.lastRecordedDate shouldBe d18
        }
    }

    describe("timezone 변경으로 인한 날짜 변경 시 Backfill") {
        it("학습일을 과거로 Backfill해도 recompute로 streak이 올바르게 합쳐진다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d17)
            streakService.recordStudied(userId = USER_ID, studyDate = d16) // Backfill

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 2
            streakState.lastRecordedDate shouldBe d17
        }

        it("Backfill 시 last_recorded_date가 기록의 마지막 날짜로 유지된다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d17)
            streakService.recordStudied(userId = USER_ID, studyDate = d16) // Backfill

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.lastRecordedDate shouldBe d17
        }
    }

    describe("sync") {
        context("timezone을 수정해 날짜 경계를 넘는 경우") {
            it("날짜 경계를 동쪽 방향으로 넘어 streak이 끊길 경우 sync하면 current_streak이 0으로 저장된다") {
                streakService.recordStudied(userId = USER_ID, studyDate = d15)
                streakService.recordStudied(userId = USER_ID, studyDate = d16) // 마지막 기록일 d16

                // UTC+14에서는 오늘이 d18이라 끊김
                val now = Instant.parse("2026-06-17T11:00:00Z")
                streakService.sync(
                    userId = USER_ID,
                    now = now,
                    timezone = ZoneOffset.ofHours(14), // UTC +14
                    dayCutoffHour = 0,
                )

                val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
                streakState.currentStreak shouldBe 0
                streakState.lastRecordedDate shouldBe d16
            }

            it("날짜 경계를 서쪽 방향으로 넘어 streak이 유효할 경우 sync하면 재계산으로 복구한다") {
                streakService.recordStudied(userId = USER_ID, studyDate = d15)
                streakService.recordStudied(userId = USER_ID, studyDate = d16) // 마지막 기록일 d16
                val now = Instant.parse("2026-06-17T11:00:00Z")
                streakService.sync( // UTC+14에서는 오늘이 d18이라 streak을 0으로 세팅
                    userId = USER_ID,
                    now = now,
                    timezone = ZoneOffset.ofHours(14),
                    dayCutoffHour = 0,
                )

                // UTC-12에서는 오늘이 d16이라 재계산을 통해 복구
                streakService.sync(
                    userId = USER_ID,
                    now = now,
                    timezone = ZoneOffset.ofHours(-12), // UTC -12
                    dayCutoffHour = 0,
                )
                val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
                streakState.currentStreak shouldBe 2
                streakState.lastRecordedDate shouldBe d16
            }
        }

        it("학습 이력이 없으면 streak_state를 만들지 않는다") {
            streakService.sync(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)
            streakState shouldBe null
        }

        it("dayCutoffHour를 적용해 클라이언트 로컬 시각의 날짜를 보정한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            // d18 02:00 + cutoff 4시 = 보정 날짜는 d17
            streakService.sync(
                userId = USER_ID,
                now = instantOn(d18, hour = 2), // 요청이 들어온 시간
                timezone = kst,
                dayCutoffHour = 4,
            )

            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 2
            streakState.lastRecordedDate shouldBe d16
        }
    }

    describe("getStreak") {
        it("학습 이력이 없으면 `NotStarted`을 반환한다") {
            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )
            result shouldBe StreakStatusResult.NotStarted
        }

        it("오늘이 마지막 기록일이면 저장된 streak이 포함된 `Active`를 반환한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )

            result.shouldBeTypeOf<StreakStatusResult.Active>()
            result.currentStreak shouldBe 2
        }

        it("마지막 기록일이 어제이면 streak이 깨지지 않았으므로 저장된 streak이 포함된 `Active`를 반환한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d17),
                timezone = kst,
                dayCutoffHour = 0,
            )

            result.shouldBeTypeOf<StreakStatusResult.Active>()
            result.currentStreak shouldBe 2
        }

        it("마지막 기록일로부터 이틀 이상 지나면 `Broken`을 반환한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d18),
                timezone = kst,
                dayCutoffHour = 0,
            )
            result shouldBe StreakStatusResult.Broken
        }

        it("오늘이 마지막 기록보다 과거여도 streak이 살아있는 것으로 보고 저장된 streak이 포함된 `Active`를 반환한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            // timezone을 서쪽으로 이동하여 날짜 경계를 넘을 경우
            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d15),
                timezone = kst,
                dayCutoffHour = 0,
            )

            result.shouldBeTypeOf<StreakStatusResult.Active>()
            result.currentStreak shouldBe 2
        }

        it("끊긴 상태를 조회해도 저장된 current_streak을 변경하지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d18),
                timezone = kst,
                dayCutoffHour = 0,
            )

            result shouldBe StreakStatusResult.Broken
            val streakState = streakStateRepository.findByUserId(userId = USER_ID)!!
            streakState.currentStreak shouldBe 2
        }

        it("dayCutoffHour를 적용해 경계 시각의 날짜를 보정한다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            // d18 02:00 + cutoff 4시 = 보정 날짜는 d17
            val result = streakService.getStreak(
                userId = USER_ID,
                now = instantOn(d18, hour = 2),
                timezone = kst,
                dayCutoffHour = 4,
            )
            result.shouldBeTypeOf<StreakStatusResult.Active>()
            result.currentStreak shouldBe 2
        }

        it("같은 시간에 요청을 보내도 timezone에 따라 날짜가 변경된다면 streak 판정이 바뀐다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            streakService.recordStudied(userId = USER_ID, studyDate = d16) // 마지막 기록일 d16

            val now = Instant.parse("2026-06-17T11:00:00Z")
            // UTC-12 → 오늘 d16
            val result1 = streakService.getStreak(
                userId = USER_ID,
                now = now,
                timezone = ZoneOffset.ofHours(-12),
                dayCutoffHour = 0,
            )
            result1.shouldBeTypeOf<StreakStatusResult.Active>()
            result1.currentStreak shouldBe 2 // 클라이언트는 날짜는 16일이므로 streak이 이어짐

            // UTC+14 → 오늘 d18
            val result2 = streakService.getStreak(
                userId = USER_ID,
                now = now,
                timezone = ZoneOffset.ofHours(14),
                dayCutoffHour = 0,
            )
            result2 shouldBe StreakStatusResult.Broken // 클라이언트 날짜는 18일이므로 streak이 끊김
        }
    }

    describe("syncWithRestDayCheck") {
        it("휴식일이고 오늘 기록이 없으면 REST_DAY를 기록하고 streak을 이어준다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            every { restDayCheckService.isRestDay(any(), any(), any(), any()) } returns true

            streakService.syncWithRestDayCheck(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 2
            studyDays.first().streakType shouldBe StreakType.REST_DAY
            studyDays.first().studyDate shouldBe d16
            streakStateRepository.findByUserId(userId = USER_ID)!!.currentStreak shouldBe 1
        }

        it("오늘 이미 학습 기록이 있으면 휴식일 판정을 건너뛴다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d16)

            streakService.syncWithRestDayCheck(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )

            verify(exactly = 0) { restDayCheckService.isRestDay(any(), any(), any(), any()) }
            studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID) shouldHaveSize 1
        }

        it("휴식일이 아니면 REST_DAY를 기록하지 않는다") {
            streakService.recordStudied(userId = USER_ID, studyDate = d15)
            every { restDayCheckService.isRestDay(any(), any(), any(), any()) } returns false

            streakService.syncWithRestDayCheck(
                userId = USER_ID,
                now = instantOn(d16),
                timezone = kst,
                dayCutoffHour = 0,
            )

            val studyDays = studyDayRepository.findAllByUserIdOrderByStudyDateDesc(userId = USER_ID)
            studyDays shouldHaveSize 1
            studyDays.none { it.streakType == StreakType.REST_DAY } shouldBe true
        }
    }
}) {
    companion object {
        private const val USER_ID = 1L
        private const val OTHER_USER_ID = 2L
    }
}
