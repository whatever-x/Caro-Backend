package com.whatever.caro.banner.internal

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Instant

@ConfigurationProperties("caro.home-banners")
data class HomeBannerProperties(
    val configRevision: String = "empty",
    val allowedImageHosts: Set<String> = emptySet(),
    val items: List<ConfiguredBanner> = emptyList(),
) {
    fun validate() {
        require(configRevision.isNotBlank()) { "home-banners.config-revision must not be blank" }
        require(items.map { it.id }.distinct().size == items.size) { "Duplicate home banner ids" }
        items.forEach { item ->
            require(item.id.isNotBlank() && item.revision > 0) { "Invalid home banner id or revision" }
            require(item.accessibleLabel.isNotBlank()) { "Home banner accessible label is required" }
            require(item.aspectRatio == 3.0) { "Home banner aspect ratio must be 3:1" }
            require(item.startsAt < item.endsAt) { "Invalid home banner period" }
            val image = URI(item.imageUrl)
            require(
                image.scheme == "https" && image.host in allowedImageHosts &&
                    image.rawUserInfo == null && image.port == -1 && image.rawFragment == null,
            ) { "Home banner image must use an allowed HTTPS host" }
            require(
                when (item.destination.type) {
                    BannerDestinationType.LIBRARY_LIST -> item.destination.id == null
                    BannerDestinationType.LIBRARY_DECK -> item.destination.id?.toLongOrNull()?.let { it > 0 } == true
                },
            ) { "Invalid home banner destination" }
            listOfNotNull(item.minAndroidVersion, item.minIosVersion).forEach { AppVersion.parse(it) }
        }
    }
}

data class ConfiguredBanner(
    val id: String,
    val revision: Long,
    val imageUrl: String,
    val accessibleLabel: String,
    val destination: BannerDestination,
    val startsAt: Instant,
    val endsAt: Instant,
    val aspectRatio: Double = 3.0,
    val active: Boolean = false,
    val priority: Int = 0,
    val minAndroidVersion: String? = null,
    val minIosVersion: String? = null,
)

enum class BannerPlatform { ANDROID, IOS }

enum class BannerDestinationType { LIBRARY_LIST, LIBRARY_DECK }

data class BannerDestination(
    val type: BannerDestinationType,
    val id: String? = null,
)

internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    override fun compareTo(
        other: AppVersion,
    ): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(
            value: String,
        ): AppVersion {
            require(value.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+"))) { "Invalid app version" }
            val parts = value.split('.').map { requireNotNull(it.toIntOrNull()) { "Invalid app version" } }
            return AppVersion(parts[0], parts[1], parts[2])
        }
    }
}
