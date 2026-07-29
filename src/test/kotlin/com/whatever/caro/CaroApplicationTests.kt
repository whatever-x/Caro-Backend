package com.whatever.caro

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import

/**
 * 전체 애플리케이션 컨텍스트 테스트
 *
 * @AutoConfigureMockMvc는 이 테스트에서 사용하지 않지만,
 * PublicEndpointsOpenApiConsistencyTest 와 동일한 TestContext 캐시 키를 만들어
 * 전체 앱 컨텍스트가 중복으로 뜨는 것을 막기 위해 유지
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
class CaroApplicationTests {

    @Test
    fun contextLoads() {
    }
}
