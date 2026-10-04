package com.whatever.caro.notification

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * 푸시 알림(FCM) 모듈.
 * 기기 토큰 관리와 발송을 담당하며, 무엇을 언제 보낼지는 다른 모듈의 이벤트/스케줄에서 결정한다.
 */
@PackageInfo
@ApplicationModule(
    displayName = "Notification",
    allowedDependencies = ["common", "study"],
)
class ModuleMetadata
