package com.whatever.caro.notification.internal

import com.whatever.caro.common.entity.BaseTimeEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class DevicePlatform {
    ANDROID,
    IOS,
}

@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @Column(name = "user_id", nullable = false)
    var userId: Long,

    @Column(nullable = false, unique = true, length = 512)
    val token: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var platform: DevicePlatform,

    /** 기기 타임존. 스트릭 리마인더를 현지 시각에 보내는 데 쓴다. */
    @Column(nullable = false, length = 64)
    var timezone: ZoneId,

    /** 기기 언어(Accept-Language). 푸시 문구 다국어 처리에 쓴다. */
    @Column(nullable = false, length = 35)
    var locale: Locale,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /**
     * 같은 토큰이 다시 등록되면 소유자/플랫폼/타임존/언어를 갱신한다.
     * 한 기기에서 계정을 바꿔 로그인한 경우 이전 계정으로 푸시가 가지 않도록 userId를 재할당한다.
     */
    fun refresh(
        userId: Long,
        platform: DevicePlatform,
        timezone: ZoneId,
        locale: Locale,
        now: Instant,
    ) {
        this.userId = userId
        this.platform = platform
        this.timezone = timezone
        this.locale = locale
        this.lastSeenAt = now
    }
}
