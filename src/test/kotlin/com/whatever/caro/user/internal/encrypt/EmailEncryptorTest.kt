package com.whatever.caro.user.internal.encrypt

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

class EmailEncryptorTest :
    DescribeSpec({

        // 32바이트 ASCII의 base64 (테스트 전용 더미 키)
        val properties = EmailEncryptorProperties(
            encryptionKey = "dGVzdC1lbmNyeXB0aW9uLWtleS0wMTIzNDU2Nzg5YWI=",
            blindIndexKey = "dGVzdC1ibGluZGluZGV4LWtleS0wMTIzNDU2Nzg5YWI=",
        )
        val encryptor = EmailEncryptor(properties)

        describe("encrypt / decrypt") {
            it("암호화 후 복호화하면 원본 이메일이 나온다 (왕복)") {
                val email = "foo@example.com"

                val encrypted = encryptor.encrypt(email)
                val decrypted = encryptor.decrypt(encrypted)

                decrypted shouldBe email
            }

            it("같은 이메일이라도 암호화할 때마다 다른 암호문이 된다 (IV 랜덤)") {
                val email = "foo@example.com"

                val first = encryptor.encrypt(email)
                val second = encryptor.encrypt(email)

                first shouldNotBe second
                // 비결정적이어도 둘 다 같은 평문으로 복호화돼야 한다
                encryptor.decrypt(first) shouldBe email
                encryptor.decrypt(second) shouldBe email
            }

            it("null / blank 는 null 을 반환한다") {
                encryptor.encrypt(null) shouldBe null
                encryptor.encrypt("") shouldBe null
                encryptor.encrypt("  ") shouldBe null
                encryptor.decrypt(null) shouldBe null
                encryptor.decrypt("") shouldBe null
            }

            it("변조된 암호문은 EmailDecryptionException 을 던진다 (tag 검증 실패)") {
                val encrypted = encryptor.encrypt("foo@example.com")!!

                // 마지막 바이트(tag 일부)를 1비트 뒤집어 변조
                val raw = Base64.getDecoder().decode(encrypted)
                raw[raw.size - 1] = (raw[raw.size - 1].toInt() xor 0x01).toByte()
                val tampered = Base64.getEncoder().encodeToString(raw)

                shouldThrow<EmailDecryptionException> {
                    encryptor.decrypt(tampered)
                }
            }

            it("base64 가 아닌 손상된 값도 EmailDecryptionException 으로 감싼다") {
                shouldThrow<EmailDecryptionException> {
                    encryptor.decrypt("not_a_valid_base64!!!")
                }
            }
        }
    })
