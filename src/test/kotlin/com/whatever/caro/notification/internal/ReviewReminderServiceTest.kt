package com.whatever.caro.notification.internal

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class ReviewReminderServiceTest :
    DescribeSpec({
        val repository = mockk<ReviewReminderSettingRepository>()
        val service = ReviewReminderService(reviewReminderSettingRepository = repository)

        beforeEach { clearAllMocks() }

        describe("updateSetting") {
            it("설정이 없으면 새로 저장한다") {
                every { repository.findByUserId(userId = 1L) } returns null
                val saved = slot<ReviewReminderSetting>()
                every { repository.save(capture(saved)) } answers { saved.captured }

                service.updateSetting(userId = 1L, enabled = true, reminderHour = 21)

                saved.captured.userId shouldBe 1L
                saved.captured.enabled shouldBe true
                saved.captured.reminderHour shouldBe 21
            }

            it("설정이 있으면 기존 행을 갱신하고 새로 저장하지 않는다") {
                val existing = ReviewReminderSetting(userId = 1L, enabled = false, reminderHour = 9)
                every { repository.findByUserId(userId = 1L) } returns existing

                service.updateSetting(userId = 1L, enabled = true, reminderHour = 22)

                existing.enabled shouldBe true
                existing.reminderHour shouldBe 22
                verify(exactly = 0) { repository.save(any()) }
            }

            it("해제는 enabled=false로 저장한다") {
                val existing = ReviewReminderSetting(userId = 1L, enabled = true, reminderHour = 21)
                every { repository.findByUserId(userId = 1L) } returns existing

                service.updateSetting(userId = 1L, enabled = false, reminderHour = 21)

                existing.enabled shouldBe false
            }
        }

        describe("getSetting") {
            it("저장된 설정을 그대로 반환한다") {
                val existing = ReviewReminderSetting(userId = 1L, enabled = true, reminderHour = 8)
                every { repository.findByUserId(userId = 1L) } returns existing

                service.getSetting(userId = 1L) shouldBe existing
            }

            it("설정이 없으면 null을 반환한다") {
                every { repository.findByUserId(userId = 1L) } returns null

                service.getSetting(userId = 1L) shouldBe null
            }
        }
    })
