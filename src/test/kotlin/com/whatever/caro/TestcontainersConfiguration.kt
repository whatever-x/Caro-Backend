package com.whatever.caro

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.context.ImportTestcontainers
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.testcontainers.containers.GenericContainer
import org.testcontainers.mysql.MySQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 테스트 전역에서 공유하는 MySQL / Redis 컨테이너.
 *
 * 컨테이너를 @Bean으로 두면 테스트 컨텍스트(모듈 테스트마다 별도)가 뜰 때마다 새 컨테이너가 생성된다.
 * static 필드(companion @JvmField)로 선언하고 @ImportTestcontainers로 가져오면
 * JVM당 한 번만 기동되어 모든 컨텍스트가 같은 컨테이너를 공유한다.
 * 컨테이너는 Spring이 아닌 Testcontainers(Ryuk)가 JVM 종료 시 정리한다.
 */
@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers
class TestcontainersConfiguration {

    companion object {
        private const val MYSQL_VERSION = "mysql:8.4"
        private const val REDIS_VERSION = "redis:8.4"

        @JvmField
        @field:ServiceConnection
        val mysqlContainer: MySQLContainer =
            MySQLContainer(DockerImageName.parse(MYSQL_VERSION))
                .withUrlParam("connectionTimeZone", "UTC")

        @JvmField
        @field:ServiceConnection(name = "redis")
        val redisContainer: GenericContainer<*> =
            GenericContainer(DockerImageName.parse(REDIS_VERSION)).withExposedPorts(6379)
    }
}
