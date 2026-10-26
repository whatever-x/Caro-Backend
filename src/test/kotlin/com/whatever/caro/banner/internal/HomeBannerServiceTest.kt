package com.whatever.caro.banner.internal

import com.whatever.caro.CaroModuleTest
import com.whatever.caro.card.api.library.LibraryCatalogApi
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import

@TestConfiguration
class MockLibraryCatalogConfig {
    @Bean
    fun libraryCatalogApi(): LibraryCatalogApi = mockk()
}

@CaroModuleTest(extraIncludes = ["common"])
@Import(MockLibraryCatalogConfig::class)
class HomeBannerServiceTest(
    service: HomeBannerService,
) : DescribeSpec({
    it("빈 운영 설정을 바인딩하고 다른 모듈 없이 정상 빈 목록을 제공한다") {
        service.list(BannerPlatform.ANDROID, "1.0.0").items shouldBe emptyList()
    }
})
