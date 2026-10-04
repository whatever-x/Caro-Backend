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

    /** ZoneId 문자열 (예: Asia/Seoul). 스트릭 리마인더를 현지 시각에 보내는 데 쓴다. */
    @Column(nullable = false, length = 64)
    var timezone: String,

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant,
) : BaseTimeEntity() {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L

    /**
     * 같은 토큰이 다시 등록되면 소유자/플랫폼/타임존을 갱신한다.
     * 한 기기에서 계정을 바꿔 로그인한 경우 이전 계정으로 푸시가 가지 않도록 userId를 재할당한다.
     */
    fun refresh(
        userId: Long,
        platform: DevicePlatform,
        timezone: String,
        now: Instant,
    ) {
        this.userId = userId
        this.platform = platform
        this.timezone = timezone
        this.lastSeenAt = now
    }
}
