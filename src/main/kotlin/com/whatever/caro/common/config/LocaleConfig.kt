package com.whatever.caro.common.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.LocaleResolver
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver
import java.util.Locale

@Configuration
class LocaleConfig {
    @Bean
    fun localeResolver(): LocaleResolver =
        AcceptHeaderLocaleResolver().apply {
            setDefaultLocale(Locale.ENGLISH)
        }
}
