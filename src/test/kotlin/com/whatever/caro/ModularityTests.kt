package com.whatever.caro

import io.kotest.core.spec.style.DescribeSpec
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

/**
 * Spring Modulith 모듈 구조 검증 테스트
 *
 * 이 테스트는 다음을 자동으로 검증합니다:
 * - 모듈 간 의존성 규칙 준수
 * - 순환 참조 방지
 * - 패키지 경계 위반 감지
 * - 무단 접근 차단
 */
class ModularityTests :
    DescribeSpec({

        val modules = ApplicationModules.of(CaroApplication::class.java)

        describe("verifyModularStructure") {
            it("모듈 간 의존성 규칙을 검증한다") {
                modules.verify()
            }

            // TODO 모듈 생성 뒤 복구
//            it("모듈이 올바르게 감지되었는지 확인한다") {
//                val moduleCount = modules.stream().count()
//                moduleCount shouldBe 2
//            }
        }

        describe("generateDocumentation") {
            it("모듈 구조를 문서화한다") {
                // build/spring-modulith-docs 디렉토리에 문서 생성:
                // - components.puml (PlantUML 다이어그램)
                // - module-structure.adoc (Asciidoc 문서)
                Documenter(modules).writeDocumentation()
            }

            it("문서 생성 경로를 커스터마이즈할 수 있다") {
                // 특정 디렉토리에 문서 생성 가능
                Documenter(modules).writeDocumentation(
                    Documenter.DiagramOptions.defaults(),
                    Documenter.CanvasOptions.defaults(),
                )
            }
        }
    })
