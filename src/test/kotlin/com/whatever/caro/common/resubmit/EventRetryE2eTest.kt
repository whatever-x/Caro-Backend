package com.whatever.caro.common.resubmit

import com.whatever.caro.CaroModuleTest
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.awaitility.Awaitility.await
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.modulith.events.ApplicationModuleListener
import org.springframework.modulith.events.IncompleteEventPublications
import org.springframework.modulith.events.ResubmissionOptions
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 실패 이벤트 재시도 e2e.
 *
 * 리스너가 1차에 실패하면 event_publication 에 미완료로 남고,
 * resubmit 하면 같은 이벤트가 리스너에 다시 전달되어(at-least-once) 2차에 성공해 완료되는
 * "실패 → 재시도 → 복구" 전체 흐름을 검증한다.
 */
@CaroModuleTest(extraIncludes = ["common"])
@Import(EventRetryE2eTest.FailOnceConfig::class)
class EventRetryE2eTest(
    private val publisher: ApplicationEventPublisher,
    private val incompleteEventPublications: IncompleteEventPublications,
    private val transactionTemplate: TransactionTemplate,
    private val jdbcTemplate: JdbcTemplate,
) : DescribeSpec({

    // FailOnceListener 리스너의 '미완료(completion_date IS NULL)' 발행 건수.
    // 완료되면 (archive 모드에서는) event_publication 에서 사라지므로 미완료 건수 1→0 으로 완료를 검증한다.
    fun incompleteCount(): Int =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FROM event_publication
            WHERE listener_id LIKE '%FailOnceListener%'
              AND completion_date IS NULL
            """.trimIndent(),
            Int::class.java,
        ) ?: 0

    afterTest {
        attempts.set(0)
    }

    describe("실패 이벤트 재시도") {
        it("리스너가 1차 실패하면 미완료로 남고, resubmit 하면 재처리되어 완료된다") {
            // 1. 이벤트 발행 (커밋 후 리스너가 async 실행)
            transactionTemplate.execute {
                publisher.publishEvent(RetryTestEvent(id = 1L))
            }

            // 2. 1차 시도 → 강제 실패 → attempts == 1
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                attempts.get() shouldBe 1
            }

            // 3. DB 상태: 1차 실패로 미완료(completion_date IS NULL) 1건 남아있다
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                incompleteCount() shouldBe 1
            }

            // 4. resubmit 트리거 (minAge 0 → 방금 발행한 미완료 건도 대상에 포함)
            incompleteEventPublications.resubmitIncompletePublications(
                ResubmissionOptions.defaults().withMinAge(Duration.ZERO),
            )

            // 5. 2차 시도 → 성공 → attempts == 2 (리스너가 재실행됨)
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                attempts.get() shouldBe 2
            }

            // 6. DB 상태: 완료 처리되어 미완료 건이 0건이 된다 (재처리로 복구 완료)
            await().atMost(5, TimeUnit.SECONDS).untilAsserted {
                incompleteCount() shouldBe 0
            }
        }
    }
}) {
    companion object {
        val attempts = AtomicInteger(0)
    }

    data class RetryTestEvent(
        val id: Long,
    )

    open class FailOnceListener {
        @ApplicationModuleListener
        open fun on(
            event: RetryTestEvent,
        ) {
            val attempt = attempts.incrementAndGet()
            if (attempt == 1) {
                throw RuntimeException("1차 강제 실패 (재시도 검증용)")
            }
            // 2차부터 성공
        }
    }

    @TestConfiguration
    class FailOnceConfig {
        @Bean
        fun failOnceListener(): FailOnceListener = FailOnceListener()
    }
}
