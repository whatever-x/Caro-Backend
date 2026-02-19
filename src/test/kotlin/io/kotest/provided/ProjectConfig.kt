package io.kotest.provided

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.extensions.spring.SpringExtension

/**
 * Kotest 전역 설정
 *
 * Spring Boot 통합을 위한 SpringExtension 활성화
 * - Spring ApplicationContext 초기화
 * - @Autowired dependency injection 활성화
 * - @SpringBootTest, @ApplicationModuleTest 등 모든 Spring 테스트 어노테이션 지원
 *
 * 적용 대상
 * - @SpringBootTest (전체 애플리케이션 컨텍스트)
 * - @ApplicationModuleTest (Spring Modulith 모듈 테스트)
 * - @DataJpaTest, @WebMvcTest 등 모든 Spring slice test
 */
object ProjectConfig : AbstractProjectConfig() {
    override val extensions = listOf(SpringExtension())
}
