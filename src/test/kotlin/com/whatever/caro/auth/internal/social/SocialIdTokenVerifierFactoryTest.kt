package com.whatever.caro.auth.internal.social

import com.whatever.caro.auth.exception.InvalidSocialTokenException
import com.whatever.caro.user.SocialProvider
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk

class SocialIdTokenVerifierFactoryTest :
    DescribeSpec({

        val googleVerifier = mockk<SocialIdTokenVerifier>()
        val appleVerifier = mockk<SocialIdTokenVerifier>()

        every { googleVerifier.provider } returns SocialProvider.GOOGLE
        every { appleVerifier.provider } returns SocialProvider.APPLE

        val factory = SocialIdTokenVerifierFactory(listOf(googleVerifier, appleVerifier))

        describe("getVerifier") {
            it("GOOGLE provider 요청 시 Google verifier를 반환한다") {
                val result = factory.getVerifier(SocialProvider.GOOGLE)

                result shouldBe googleVerifier
            }

            it("APPLE provider 요청 시 Apple verifier를 반환한다") {
                val result = factory.getVerifier(SocialProvider.APPLE)

                result shouldBe appleVerifier
            }

            it("등록되지 않은 provider 요청 시 InvalidSocialTokenException을 던진다") {
                val emptyFactory = SocialIdTokenVerifierFactory(emptyList())

                val exception = shouldThrow<InvalidSocialTokenException> {
                    emptyFactory.getVerifier(SocialProvider.GOOGLE)
                }

                exception.message shouldContain SocialProvider.GOOGLE.name
            }
        }
    })
