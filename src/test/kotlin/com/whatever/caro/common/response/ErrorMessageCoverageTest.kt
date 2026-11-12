package com.whatever.caro.common.response

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.type.filter.AssignableTypeFilter
import java.util.Properties

class ErrorMessageCoverageTest :
    DescribeSpec({
        it("모든 오류 코드가 영어와 한국어 리소스에 존재한다") {
            fun messages(
                name: String,
            ): Properties =
                Properties().apply {
                    ErrorCodeSpec::class.java.getResourceAsStream("/$name")!!.reader(Charsets.UTF_8).use { load(it) }
                }
            val english = messages("messages.properties")
            val korean = messages("messages_ko.properties")
            english.keys shouldBe korean.keys
            val scanner = ClassPathScanningCandidateComponentProvider(false).apply {
                addIncludeFilter(AssignableTypeFilter(ErrorCodeSpec::class.java))
            }
            val codes = scanner.findCandidateComponents("com.whatever.caro").flatMap { bean ->
                Class.forName(bean.beanClassName!!).enumConstants?.map { it as ErrorCodeSpec }.orEmpty()
            }
            codes.isEmpty() shouldBe false
            codes.forEach { code ->
                val en = english.getProperty(code.messageKey)
                val ko = korean.getProperty(code.messageKey)
                en shouldNotBe null
                ko shouldNotBe null
                en!!.isBlank() shouldBe false
                ko!!.isBlank() shouldBe false
                Regex("[가-힣]").containsMatchIn(en) shouldBe false
                en shouldNotBe ko
            }
        }
    })
