package com.whatever.caro.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.auditing.DateTimeProvider
import org.springframework.data.jpa.repository.config.EnableJpaAuditing
import java.time.Clock
import java.util.Optional

@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "dateTimeProvider")
class JpaAuditingConfig {
    @Bean
    fun dateTimeProvider(
        clock: Clock,
    ): DateTimeProvider = DateTimeProvider { Optional.of(clock.instant()) }
}
