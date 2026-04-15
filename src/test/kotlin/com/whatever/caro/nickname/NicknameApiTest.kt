package com.whatever.caro.nickname

import com.whatever.caro.nickname.internal.NicknameService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.Locale

private val logger = KotlinLogging.logger {}

class NicknameApiTest :
    DescribeSpec({

        val nicknameApi: NicknameApi = NicknameService()
        val englishRegex = "^[A-Za-z]+$".toRegex()
        val koreanRegex = "^[가-힣]+$".toRegex()

        describe("NicknameApi") {

            describe("create") {
                it("NicknameBuilder 인스턴스를 반환한다") {
                    val builder = nicknameApi.create()

                    builder.shouldBeInstanceOf<NicknameBuilder>()
                }
            }

            describe("randomName") {
                it("기본 locale(영어)로 닉네임을 생성한다") {
                    val name = nicknameApi.randomName().also { logger.info { it } }

                    name shouldContain "_"
                    name.split("_") shouldHaveSize 2
                    name.split("_").forEach { part -> part shouldMatch englishRegex }
                }

                it("한국어 locale로 닉네임을 생성한다") {
                    val name = nicknameApi.randomName(Locale.KOREAN).also { logger.info { it } }

                    name shouldContain "_"
                    name.split("_") shouldHaveSize 2
                    name.split("_").forEach { part -> part shouldMatch koreanRegex }
                }
            }
        }

        describe("NicknameBuilder") {
            it("adjective().noun() - 2단어 닉네임을 생성한다") {
                val name = nicknameApi.create()
                    .adjective()
                    .noun()
                    .get().also { logger.info { it } }

                name.shouldNotBeEmpty()
                name.split("_") shouldHaveSize 2
            }

            it("adjective().color().animal() - 3단어 닉네임을 생성한다") {
                val name = nicknameApi.create()
                    .adjective()
                    .color()
                    .animal()
                    .get().also { logger.info { it } }

                name.shouldNotBeEmpty()
                name.split("_") shouldHaveSize 3
            }

            it("모든 8개 카테고리 메서드 체이닝이 가능하다") {
                val name = nicknameApi.create()
                    .adjective()
                    .animal()
                    .color()
                    .noun()
                    .person()
                    .mythical()
                    .legend()
                    .food()
                    .get().also { logger.info { it } }

                name.shouldNotBeEmpty()
                name.split("_") shouldHaveSize 8
            }

            it("같은 카테고리를 두 번 호출하면 두 단어를 생성한다") {
                val name = nicknameApi.create()
                    .adjective()
                    .adjective()
                    .get().also { logger.info { it } }

                name.shouldNotBeEmpty()
                name.split("_") shouldHaveSize 2
            }

            it("anyNoun() - 여러 번 호출 시 다른 단어를 생성한다") {
                val builder = nicknameApi.create().anyNoun()
                val names = (1..50).map { builder.get() }.toSet()

                names.size shouldBeGreaterThan 1
            }

            it("withSeparator(\"@\") - 커스텀 구분자를 적용한다") {
                val customSeparator = "@"
                val name = nicknameApi.create()
                    .adjective()
                    .noun()
                    .withSeparator(customSeparator)
                    .get().also { logger.info { it } }

                name.shouldNotBeEmpty()
                name shouldContain customSeparator
                name shouldNotContain "_" // default separator
                name.split(customSeparator) shouldHaveSize 2
            }

            it("카테고리 없이 get() 호출 시 IllegalArgumentException 발생") {
                val builder = nicknameApi.create()

                shouldThrow<IllegalArgumentException> {
                    builder.get()
                }
            }

            it("빌더 메서드가 새 인스턴스를 반환한다(원본 빌더에 카테고리를 추가해도 원본은 불변)") {
                val original = nicknameApi.create()
                val withAdjective = original.adjective()
                val withAdjectiveAndNoun = withAdjective.anyNoun()

                shouldThrow<IllegalArgumentException> {
                    original.get() // original은 아무 카테고리가 없으므로 에러 발생
                }
                withAdjective.get().split("_") shouldHaveSize 1
                withAdjectiveAndNoun.get().split("_") shouldHaveSize 2
            }

            it("같은 빌더에서 get()을 여러 번 호출할 수 있다") {
                val builder = nicknameApi.create()
                    .adjective()
                    .anyNoun()

                val first = builder.get()
                val second = builder.get()

                first.shouldNotBeEmpty()
                second.shouldNotBeEmpty()
                first.split("_") shouldHaveSize 2
                second.split("_") shouldHaveSize 2
            }
        }
    })
