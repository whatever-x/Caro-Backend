package com.whatever.caro.user.internal.encrypt

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class EmailHasherTest :
    DescribeSpec({

        val properties = EmailEncryptorProperties(
            encryptionKey = "dGVzdC1lbmNyeXB0aW9uLWtleS0wMTIzNDU2Nzg5YWI=",
            blindIndexKey = "dGVzdC1ibGluZGluZGV4LWtleS0wMTIzNDU2Nzg5YWI=",
        )
        val hasher = EmailHasher(properties)

        describe("hash") {
            it("같은 이메일은 항상 같은 해시를 만든다 (결정적)") {
                hasher.hash("foo@example.com") shouldBe hasher.hash("foo@example.com")
            }

            it("대소문자/앞뒤 공백을 정규화해 같은 해시를 만든다") {
                val normalized = hasher.hash("foo@example.com")

                hasher.hash("  Foo@Example.COM  ") shouldBe normalized
                hasher.hash("FOO@EXAMPLE.COM") shouldBe normalized
            }

            it("다른 이메일은 다른 해시를 만든다") {
                hasher.hash("alice@example.com") shouldNotBe hasher.hash("bob@example.com")
            }

            it("null / blank 는 null 을 반환한다") {
                hasher.hash(null) shouldBe null
                hasher.hash("") shouldBe null
                hasher.hash("   ") shouldBe null
            }

            it("해시는 base64 44자 고정이다 (HMAC-SHA256 32바이트)") {
                hasher.hash("foo@example.com")!!.length shouldBe 44
            }
        }
    })
