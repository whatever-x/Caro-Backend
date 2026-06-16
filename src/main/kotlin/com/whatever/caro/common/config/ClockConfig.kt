package com.whatever.caro.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration

@Configuration
class ClockConfig {
    /**
     * OS마다 Clock의 정밀도 차이가 존재하며, 일관된 시간을 보장하기 위해 마이크로초 단위로 truncate한다
     */
    @Bean
    fun clock(): Clock = Clock.tick(Clock.systemUTC(), Duration.ofNanos(1000))
}
