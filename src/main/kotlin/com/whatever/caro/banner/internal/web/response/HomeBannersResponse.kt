package com.whatever.caro.banner.internal.web.response

import com.whatever.caro.banner.internal.BannerDestination
import java.time.Instant

data class HomeBannersResponse(
    val items: List<HomeBannerResponse>,
    val serverTime: Instant,
    val configRevision: String,
)

data class HomeBannerResponse(
    val id: String,
    val revision: Long,
    val imageUrl: String,
    val aspectRatio: Double,
    val accessibleLabel: String,
    val destination: BannerDestination,
    val startsAt: Instant,
    val endsAt: Instant,
)
