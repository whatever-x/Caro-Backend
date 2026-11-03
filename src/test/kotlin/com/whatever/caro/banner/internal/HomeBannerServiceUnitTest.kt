package com.whatever.caro.banner.internal

import com.whatever.caro.card.api.library.LibraryCatalogApi
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HomeBannerServiceUnitTest :
    DescribeSpec({
        val now = Instant.parse("2026-10-24T00:00:00Z")
        val clock = Clock.fixed(now, ZoneOffset.UTC)
        fun banner(
            id: String = "one",
            priority: Int = 0,
        ) = ConfiguredBanner(
            id = id,
            revision = 1,
            imageUrl = "https://images.example.com/$id.png",
            accessibleLabel = "새 콘텐츠 살펴보기",
            destination = BannerDestination(BannerDestinationType.LIBRARY_DECK, "1"),
            startsAt = now,
            endsAt = now.plusSeconds(60),
            active = true,
            priority = priority,
        )
        fun properties(
            items: List<ConfiguredBanner>,
        ) = HomeBannerProperties(
            configRevision = "revision-1",
            allowedImageHosts = setOf("images.example.com"),
            items = items,
        )

        it("시작 경계는 포함하고 종료 경계와 비활성 및 비공개 목적지는 제외한다") {
            val catalog = mockk<LibraryCatalogApi>()
            every { catalog.publishedDeckIds() } returns setOf(1L)
            val service = HomeBannerService(
                properties(
                    listOf(
                        banner("start"),
                        banner("end").copy(startsAt = now.minusSeconds(60), endsAt = now),
                        banner("future").copy(startsAt = now.plusSeconds(1)),
                        banner("inactive").copy(active = false),
                        banner("private").copy(destination = BannerDestination(BannerDestinationType.LIBRARY_DECK, "2")),
                    ),
                ),
                catalog,
                clock,
            )
            val result = service.list(BannerPlatform.ANDROID, "1.0.0")
            result.items.map { it.id } shouldBe listOf("start")
            result.serverTime shouldBe now
            result.configRevision shouldBe "revision-1"
        }

        it("priority 내림차순과 id 순으로 정렬하고 최대 다섯 개를 반환한다") {
            val catalog = mockk<LibraryCatalogApi>()
            every { catalog.publishedDeckIds() } returns setOf(1L)
            val items = (9 downTo 1).map { banner("item-$it", if (it == 8) 2 else 1) }
            HomeBannerService(properties(items), catalog, clock).list(BannerPlatform.IOS, "1.0.0")
                .items.map { it.id } shouldBe listOf("item-8", "item-1", "item-2", "item-3", "item-4")
        }

        it("플랫폼 최소 버전을 숫자로 비교하고 다른 플랫폼 제한은 적용하지 않는다") {
            val catalog = mockk<LibraryCatalogApi>()
            every { catalog.publishedDeckIds() } returns setOf(1L)
            val service = HomeBannerService(properties(listOf(banner().copy(minAndroidVersion = "1.10.0"))), catalog, clock)
            service.list(BannerPlatform.ANDROID, "1.9.9").items shouldBe emptyList()
            service.list(BannerPlatform.ANDROID, "1.10.0").items.size shouldBe 1
            service.list(BannerPlatform.IOS, "1.0.0").items.size shouldBe 1
            listOf("1.0", "v1.0.0", "1.0.0-beta", "2147483648.0.0").forEach { version ->
                shouldThrow<BusinessException> { service.list(BannerPlatform.IOS, version) }
                    .errorCode shouldBe CommonErrorCode.INVALID_INPUT
            }
        }

        it("등록 소재가 없으면 라이브러리 조회 없이 정상 빈 목록을 반환한다") {
            val catalog = mockk<LibraryCatalogApi>()
            HomeBannerService(HomeBannerProperties(), catalog, clock).list(BannerPlatform.IOS, "1.0.0")
                .items shouldBe emptyList()
            verify(exactly = 0) { catalog.publishedDeckIds() }
        }

        it("공개 제공 덱이 없으면 목록 목적지 소재도 반환하지 않는다") {
            val catalog = mockk<LibraryCatalogApi>()
            every { catalog.publishedDeckIds() } returns emptySet()
            val item = banner().copy(destination = BannerDestination(BannerDestinationType.LIBRARY_LIST))
            val service = HomeBannerService(properties(listOf(item)), catalog, clock)
            service.list(BannerPlatform.IOS, "1.0.0").items shouldBe emptyList()
            every { catalog.publishedDeckIds() } returns setOf(1L)
            service.list(BannerPlatform.IOS, "1.0.0").items.single().destination shouldBe item.destination
        }

        it("중복 id와 비허용 이미지 및 잘못된 기간과 목적지를 포함한 전체 설정을 거부한다") {
            val item = banner()
            val invalid = listOf(
                properties(listOf(item, item)),
                properties(listOf(item.copy(imageUrl = "https://other.example.com/one.png"))),
                properties(listOf(item.copy(imageUrl = "http://images.example.com/one.png"))),
                properties(listOf(item.copy(imageUrl = "https://user:pass@images.example.com/one.png"))),
                properties(listOf(item.copy(imageUrl = "https://images.example.com:8443/one.png"))),
                properties(listOf(item.copy(endsAt = item.startsAt))),
                properties(listOf(item.copy(aspectRatio = 1.0))),
                properties(listOf(item.copy(accessibleLabel = " "))),
                properties(listOf(item.copy(revision = 0))),
                properties(listOf(item.copy(destination = BannerDestination(BannerDestinationType.LIBRARY_DECK)))),
                properties(listOf(item.copy(destination = BannerDestination(BannerDestinationType.LIBRARY_LIST, "1")))),
                properties(listOf(item.copy(minAndroidVersion = "invalid"))),
            )
            invalid.forEach { shouldThrow<IllegalArgumentException> { it.validate() } }
        }
    })
