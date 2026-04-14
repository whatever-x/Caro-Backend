package com.whatever.caro.auth.internal.social

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.user.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier as GoogleVerifier

private const val TEST_ID_TOKEN = "test-id-token"

class GoogleIdTokenVerifierTest :
    DescribeSpec({

        val mockGoogleVerifier = mockk<GoogleVerifier>()
        val verifier = GoogleIdTokenVerifier(googleVerifier = mockGoogleVerifier)

        afterEach {
            clearMocks(mockGoogleVerifier)
        }

        fun stubGoogleIdToken(
            providerUserId: String,
            email: String? = null,
        ) {
            val mockPayload = mockk<GoogleIdToken.Payload>()
            val mockGoogleIdToken = mockk<GoogleIdToken>()
            every { mockGoogleVerifier.verify(any<String>()) } returns mockGoogleIdToken
            every { mockGoogleIdToken.payload } returns mockPayload
            every { mockPayload.subject } returns providerUserId
            every { mockPayload.email } returns email
        }

        describe("verify") {
            it("유효한 Google ID Token에서 SocialUserInfo를 반환한다") {
                val providerUserId = "google-user-123"
                val email = "user@example.com"
                stubGoogleIdToken(providerUserId = providerUserId, email = email)

                val result = verifier.verify(idToken = TEST_ID_TOKEN)

                result.providerUserId shouldBe providerUserId
                result.email shouldBe email
                result.provider shouldBe SocialProvider.GOOGLE
            }

            it("검증 실패(예외) 시 InvalidSocialTokenException을 던진다") {
                every { mockGoogleVerifier.verify(any<String>()) } throws RuntimeException("verification error")

                val exception = shouldThrow<InvalidSocialTokenException> {
                    verifier.verify(idToken = TEST_ID_TOKEN)
                }

                exception.message shouldContain "Google ID Token 검증 실패"
                exception.cause.shouldBeInstanceOf<RuntimeException>()
            }

            it("null 반환(유효하지 않은 토큰) 시 InvalidSocialTokenException을 던진다") {
                every { mockGoogleVerifier.verify(any<String>()) } returns null

                val exception = shouldThrow<InvalidSocialTokenException> {
                    verifier.verify(idToken = TEST_ID_TOKEN)
                }

                exception.message shouldContain "유효하지 않은 Google ID Token"
            }

            it("email이 null인 경우에도 정상 검증된다") {
                val providerUserId = "google-user-456"
                stubGoogleIdToken(providerUserId = providerUserId, email = null)

                val result = verifier.verify(idToken = TEST_ID_TOKEN)

                result.providerUserId shouldBe providerUserId
                result.email shouldBe null
                result.provider shouldBe SocialProvider.GOOGLE
            }
        }

        describe("provider") {
            it("GOOGLE이다") {
                verifier.provider shouldBe SocialProvider.GOOGLE
            }
        }
    })
