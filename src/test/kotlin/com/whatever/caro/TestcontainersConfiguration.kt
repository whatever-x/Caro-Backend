package com.whatever.caro

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

    // 배포 환경 DB_URL과 같은 rewriteBatchedStatements가 없으면 JDBC 배치가 행마다 문장을 보내 운영과 다른 SQL을 검증하게 된다.
    @Bean
    @ServiceConnection
    fun mysqlContainer(): MySQLContainer =
        MySQLContainer(DockerImageName.parse(MYSQL_VERSION))
            .withUrlParam("connectionTimeZone", "UTC")
            .withUrlParam("rewriteBatchedStatements", "true")

    @Bean
    @ServiceConnection(name = "redis")
    fun redisContainer(): GenericContainer<*> = GenericContainer(DockerImageName.parse(REDIS_VERSION)).withExposedPorts(6379)

    companion object {
        internal const val MYSQL_VERSION = "mysql:8.4"
        private const val REDIS_VERSION = "redis:8.4"
    }
}
