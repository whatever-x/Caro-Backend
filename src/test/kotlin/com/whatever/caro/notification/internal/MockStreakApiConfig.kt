package com.whatever.caro.notification.internal

import com.whatever.caro.study.StreakApi
import com.whatever.caro.study.StudyApi
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * notification 모듈 테스트(STANDALONE)에서는 study 모듈 빈이 뜨지 않으므로,
 * notification 컴포넌트가 의존하는 study 모듈 Api를 mock으로 제공한다.
 * - StreakApi: StreakReminderScheduler
 * - StudyApi: ReviewReminderScheduler
 */
@TestConfiguration(proxyBeanMethods = false)
class MockStreakApiConfig {
    @Bean
    fun streakApi(): StreakApi = mockk(relaxed = true)

    @Bean
    fun studyApi(): StudyApi = mockk(relaxed = true)
}
