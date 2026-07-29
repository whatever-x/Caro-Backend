package com.whatever.caro

import org.springframework.context.annotation.Import
import org.springframework.core.annotation.AliasFor
import org.springframework.modulith.test.ApplicationModuleTest

/**
 * 모듈 통합 테스트 공통 어노테이션
 *
 * @Import(TestcontainersConfiguration)을 고정해 설정 누락으로 인한 컨텍스트가 늘어나는 것을 방지.
 * extraIncludes 조합이 다를 경우 컨텍스트는 통일되지 않음.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@ApplicationModuleTest
@Import(TestcontainersConfiguration::class)
annotation class CaroModuleTest(
    @get:AliasFor(annotation = ApplicationModuleTest::class, attribute = "mode")
    val mode: ApplicationModuleTest.BootstrapMode = ApplicationModuleTest.BootstrapMode.STANDALONE,
    @get:AliasFor(annotation = ApplicationModuleTest::class, attribute = "extraIncludes")
    val extraIncludes: Array<String> = [],
)
