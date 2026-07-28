package com.whatever.caro.withdrawal

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * 회원 탈퇴 데이터 파기 조율 모듈.
 * 여러 모듈의 데이터를 삭제해야 하므로 user/study/card 의 public API 에 의존한다.
 * 의존을 여기 명시적으로 선언해 ModularityTests(경계 검증)를 통과시킨다.
 */
@PackageInfo
@ApplicationModule(
    displayName = "Withdrawal",
    allowedDependencies = ["common", "user", "study", "card :: card", "card :: deck"],
)
class ModuleMetadata
