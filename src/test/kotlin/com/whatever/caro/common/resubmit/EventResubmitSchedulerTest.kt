package com.whatever.caro.common.resubmit

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.modulith.events.EventPublication
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import java.time.Duration

class EventResubmitSchedulerTest :
    DescribeSpec({

        val incompleteEventPublications = mockk<IncompleteEventPublications>(relaxed = true)
        val scheduler = EventResubmitScheduler(
            incompleteEventPublications = incompleteEventPublications,
        )

        afterTest {
            clearMocks(incompleteEventPublications)
        }

        describe("resubmit") {
            it("미완료 이벤트 재제출을 IncompleteEventPublications 에 위임한다") {
                scheduler.resubmit()

                verify(exactly = 1) {
                    incompleteEventPublications.resubmitIncompletePublications(any<ResubmissionOptions>())
                }
            }

            it("재제출 대상은 발행 후 20분(minAge)을 넘긴 이벤트로 제한한다") {
                val optionsSlot = slot<ResubmissionOptions>()
                scheduler.resubmit()
                verify {
                    incompleteEventPublications.resubmitIncompletePublications(capture(optionsSlot))
                }

                optionsSlot.captured.minAge shouldBe Duration.ofMinutes(20)
            }

            it("completionAttempts 가 3 이상인 이벤트는 재제출 필터에서 제외한다(poison 무한 재시도 차단)") {
                val optionsSlot = slot<ResubmissionOptions>()
                scheduler.resubmit()
                verify {
                    incompleteEventPublications.resubmitIncompletePublications(capture(optionsSlot))
                }
                val filter = optionsSlot.captured.filter

                filter.test(publicationWith(completionAttempts = 2)) shouldBe true
                filter.test(publicationWith(completionAttempts = 3)) shouldBe false
            }
        }
    })

private fun publicationWith(
    completionAttempts: Int,
): EventPublication {
    val publication = mockk<EventPublication>(relaxed = true)
    every { publication.completionAttempts } returns completionAttempts
    return publication
}
