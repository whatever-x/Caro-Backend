package com.whatever.caro.banner.internal

import com.whatever.caro.banner.internal.web.response.HomeBannerResponse
import com.whatever.caro.banner.internal.web.response.HomeBannersResponse
import com.whatever.caro.card.api.library.LibraryCatalogApi
import com.whatever.caro.common.exception.BusinessException
import com.whatever.caro.common.response.CommonErrorCode
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class HomeBannerService(
    private val properties: HomeBannerProperties,
    private val catalog: LibraryCatalogApi,
    private val clock: Clock,
) {
    init {
        properties.validate()
    }

    fun list(
        platform: BannerPlatform,
        appVersion: String,
    ): HomeBannersResponse {
        val version = try {
            AppVersion.parse(appVersion)
        } catch (_: IllegalArgumentException) {
            throw BusinessException(CommonErrorCode.INVALID_INPUT)
        }
        val now = clock.instant()
        val eligible = properties.items.filter { item ->
            val minimum = when (platform) {
                BannerPlatform.ANDROID -> item.minAndroidVersion
                BannerPlatform.IOS -> item.minIosVersion
            }
            item.active && !now.isBefore(item.startsAt) && now.isBefore(item.endsAt) &&
                (minimum == null || version >= AppVersion.parse(minimum))
        }
        val published = if (eligible.isEmpty()) emptySet() else catalog.publishedDeckIds()
        val items = eligible.asSequence()
            .filter { item ->
                when (item.destination.type) {
                    BannerDestinationType.LIBRARY_LIST -> published.isNotEmpty()
                    BannerDestinationType.LIBRARY_DECK -> item.destination.id!!.toLong() in published
                }
            }
            .sortedWith(compareByDescending<ConfiguredBanner> { it.priority }.thenBy { it.id })
            .take(5)
            .map {
                HomeBannerResponse(
                    it.id,
                    it.revision,
                    it.imageUrl,
                    it.aspectRatio,
                    it.accessibleLabel,
                    it.destination,
                    it.startsAt,
                    it.endsAt,
                )
            }
            .toList()
        return HomeBannersResponse(items, now, properties.configRevision)
    }
}
