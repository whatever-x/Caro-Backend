import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit
import kotlinx.kover.gradle.plugin.dsl.GroupingEntityType

plugins {
    kotlin("jvm") version "2.3.10"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.3"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("plugin.jpa") version "2.2.21"

    id("com.diffplug.spotless") version "8.3.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.7"
}

group = "com.whatever"
version = (findProperty("version") as? String) ?: System.getenv("RELEASE_VERSION") ?: "0.0.1-SNAPSHOT"
description = "Flashcard backend"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

repositories {
    mavenCentral()
}

extra["springModulithVersion"] = "2.0.5"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
    implementation("io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0:2.24.0-alpha")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.modulith:spring-modulith-starter-jpa")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-mysql")

    runtimeOnly("com.mysql:mysql-connector-j")
    runtimeOnly("org.springframework.modulith:spring-modulith-actuator")
    runtimeOnly("org.springframework.modulith:spring-modulith-observability")
    runtimeOnly("org.springframework.modulith:spring-modulith-starter-insight")

    testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-mysql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Kotest
    testImplementation(platform("io.kotest:kotest-bom:6.1.5"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-assertions-core")
    testImplementation("io.kotest:kotest-extensions-spring")
    testImplementation("io.mockk:mockk:1.14.9")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude(
            "build/**/*.kt",
            "**/generated/**/*.kt",
        )
        ktlint("1.8.0")
            .editorConfigOverride(
                mapOf(
                    "max_line_length" to 120,
                    "ktlint_code_style" to "intellij_idea",
                    "ktlint_standard_no-wildcard-import" to "enabled",
                    "ktlint_standard_trailing-comma-on-call-site" to "enabled",
                    "ktlint_standard_trailing-comma-on-declaration-site" to "enabled",

                    // Spring 어노테이션 체인이 길어서 비활성화
                    "ktlint_standard_annotation" to "disabled",

                    "ktlint_function_signature_body_expression_wrapping" to "multiline",
                    "ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "1",
                    "ktlint_class_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than" to "1",
                ),
            )
        toggleOffOn() // spotless:off/on 주석 지원 (특정 코드 제외)
        trimTrailingWhitespace()
    }

    kotlinGradle {
        target("**/*.gradle.kts")
        ktlint("1.8.0")
    }

    // TODO yaml은 별도로 린팅
}

kover {
    reports {
        filters {
            excludes {
                classes(
                    "com.whatever.caro.CaroApplication",
                    "*ApplicationKt",
                )

                annotatedBy(
                    "org.springframework.context.annotation.Configuration",
                    "org.springframework.boot.context.properties.ConfigurationProperties",
                )

                classes(
                    "*ModuleMetadata",
                )

                classes(
                    "*TestcontainersConfiguration*",
                    "*TestCaroApplication*",
                )

                annotatedBy(
                    "jakarta.persistence.Entity",
                    "jakarta.persistence.MappedSuperclass",
                )
                classes(
                    "*Dto",
                    "*Request",
                    "*Response",
                    "*Event",
                    "*Repository",
                )
            }
        }

        total {
            html {
                onCheck = true
            }
            log {
                onCheck = true
                header = "=== Coverage Summary ==="
                format = "COVERAGE: <entity> line coverage: <value>%"
                groupBy = GroupingEntityType.APPLICATION
                coverageUnits = CoverageUnit.LINE
                aggregationForGroup = AggregationType.COVERED_PERCENTAGE
            }
        }

        verify {
            rule("Overall Coverage") {
                minBound(70)
            }
            rule("Branch Coverage") {
                bound {
                    coverageUnits = CoverageUnit.BRANCH
                    minValue = 70
                }
            }
        }
    }
}

// check 태스크에 연결
tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
