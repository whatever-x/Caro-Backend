package com.whatever.caro

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer =
        MySQLContainer(DockerImageName.parse(MYSQL_VERSION))
            .withUrlParam("connectionTimeZone", "UTC")

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> = GenericContainer(DockerImageName.parse(REDIS_VERSION)).withExposedPorts(6379)

    companion object {
        private const val MYSQL_VERSION = "mysql:8.4"
        private const val REDIS_VERSION = "redis:8.4"
    }
}
