package com.whatever.caro.common.config

import org.hibernate.cfg.AvailableSettings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.orm.jpa.hibernate.SpringBeanContainer

@Configuration
class HibernateConfig {

    @Bean
    fun beanContainerCustomizer(
        beanFactory: ConfigurableListableBeanFactory,
    ): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { hibernateProperties ->
            hibernateProperties[AvailableSettings.BEAN_CONTAINER] = SpringBeanContainer(beanFactory)
        }
}
