package com.whatever.caro.common.web.filter

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.springframework.mock.web.MockHttpServletRequest

class CachedBodyHttpServletRequestUnitTest :
    DescribeSpec({

        describe("contentAsByteArray()") {
            it("매 호출마다 원본 body byte와 동일한 복사본을 반환한다") {
                val body = "hello world".toByteArray()
                val mock = MockHttpServletRequest().apply { setContent(body) }
                val req = CachedBodyHttpServletRequest(mock)

                val body1 = req.contentAsByteArray()
                val body2 = req.contentAsByteArray()

                body1 shouldBe body
                body2 shouldBe body
                body1 shouldNotBeSameInstanceAs body2

                body1[0] = 0xFF.toByte() // 임의로 body 변경
                req.contentAsByteArray() shouldBe body // 새로 호출해도 원본 reference는 변경되지 않음
            }
        }

        describe("getInputStream()") {
            it("inputStream을 N회 호출해도 매번 읽을 수 있는 body를 반환한다") {
                val body = "hello world".toByteArray()
                val mock = MockHttpServletRequest().apply { setContent(body) }
                val req = CachedBodyHttpServletRequest(mock)

                repeat(3) {
                    req.inputStream.readBytes() shouldBe body
                }
            }
        }

        describe("getReader()") {
            it("reader를 N회 호출해도 매번 동일한 body를 디코드한다") {
                val text = "hello world"
                val body = text.toByteArray()
                val mock = MockHttpServletRequest().apply { setContent(body) }
                val req = CachedBodyHttpServletRequest(mock)

                repeat(2) {
                    req.reader.readText() shouldBe text
                }
                req.inputStream.readBytes() shouldBe body // reader 호출 후에도 inputStream 호출 가능
            }
        }

        describe("cachedBody에 대한 일관성 확인") {
            it("body에 대해 inputStream/contentAsByteArray/reader가 동일") {
                val body = "안녕 world 🚀".toByteArray()
                val mock = MockHttpServletRequest().apply { setContent(body) }
                val req = CachedBodyHttpServletRequest(mock)

                req.contentAsByteArray() shouldBe body
                req.inputStream.readBytes() shouldBe body
                req.reader.readText().toByteArray() shouldBe body
            }

            it("empty body에 대해 inputStream/contentAsByteArray/reader 모두 empty를 반환한다") {
                val mock = MockHttpServletRequest().apply { setContent(ByteArray(0)) }
                val req = CachedBodyHttpServletRequest(mock)

                req.contentAsByteArray() shouldBe ByteArray(0)
                req.inputStream.readBytes() shouldBe ByteArray(0)
                req.reader.readText() shouldBe ""
            }
        }
    })
