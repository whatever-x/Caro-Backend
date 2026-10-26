package com.whatever.caro.banner.internal

import com.whatever.caro.card.api.library.LibraryCatalogApi
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import java.time.Clock

@TestConfiguration(proxyBeanMethods = false)
@EnableConfigurationProperties(HomeBannerProperties::class)
class BannerBindingTestConfig {
    @Bean
    fun service(
        properties: HomeBannerProperties,
    ): HomeBannerService = HomeBannerService(properties, mockk<LibraryCatalogApi>(), Clock.systemUTC())
}

class HomeBannerConfigurationTest :
    DescribeSpec({
        val runner = ApplicationContextRunner().withUserConfiguration(BannerBindingTestConfig::class.java)
        val values = arrayOf(
            "caro.home-banners.config-revision=config-1",
            "caro.home-banners.allowed-image-hosts[0]=images.example.com",
            "caro.home-banners.items[0].id=one",
            "caro.home-banners.items[0].revision=1",
            "caro.home-banners.items[0].image-url=https://images.example.com/one.png",
            "caro.home-banners.items[0].accessible-label=콘텐츠 살펴보기",
            "caro.home-banners.items[0].destination.type=LIBRARY_LIST",
            "caro.home-banners.items[0].starts-at=2026-10-24T00:00:00Z",
            "caro.home-banners.items[0].ends-at=2026-10-25T00:00:00Z",
            "caro.home-banners.items[0].active=true",
        )
        it("외부 설정의 중첩 목록과 UTC 기간을 바인딩한다") {
            runner.withPropertyValues(*values).run { context ->
                context.startupFailure shouldBe null
                val properties = context.getBean(HomeBannerProperties::class.java)
                properties.configRevision shouldBe "config-1"
                properties.items.single().active shouldBe true
                properties.items.single().startsAt.toString() shouldBe "2026-10-24T00:00:00Z"
            }
        }
        it("기본 빈 설정으로 시작하고 잘못된 설정 묶음의 활성화는 거부한다") {
            runner.run { it.startupFailure shouldBe null }
            runner.withPropertyValues(*values, "caro.home-banners.items[0].revision=0").run { context ->
                (context.startupFailure != null) shouldBe true
            }
        }
    })
