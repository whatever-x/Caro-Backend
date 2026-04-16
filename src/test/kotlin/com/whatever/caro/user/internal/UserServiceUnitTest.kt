package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.dao.DataIntegrityViolationException

class UserServiceUnitTest :
    DescribeSpec({

        val mockUserRepo = mockk<UserRepository>(relaxed = true)
        val mockSocialRepo = mockk<SocialAccountRepository>(relaxed = true)
        val userApi: UserApi = UserService(userRepository = mockUserRepo, socialAccountRepository = mockSocialRepo)

        fun createUserWithId(
            id: Long,
            nickname: String = "existing",
        ): User {
            val user = User(nickname = nickname)
            val idField = User::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(user, id)
            return user
        }

        describe("createSocialUser - race condition") {
            it("중복 생성 시 기존 유저를 반환한다") {
                val existingUser = createUserWithId(1L)
                val existingSocialAccount = SocialAccount(
                    user = existingUser,
                    provider = SocialProvider.GOOGLE,
                    providerUserId = "google-123",
                )

                every { mockUserRepo.save(any()) } throws DataIntegrityViolationException("race condition으로 인한 중복")
                every {
                    mockSocialRepo.findByProviderAndProviderUserId(
                        provider = existingSocialAccount.provider,
                        providerUserId = existingSocialAccount.providerUserId,
                    )
                } returns existingSocialAccount

                val result = userApi.createSocialUser(
                    // 후행 요청
                    provider = existingSocialAccount.provider,
                    providerUserId = existingSocialAccount.providerUserId,
                    email = "test@email.com",
                )

                result.id shouldBe existingUser.id
            }

            it("fallback 후, 조회되는 유저가 없다면 예외를 re-throw한다") {
                val provider = SocialProvider.GOOGLE
                val providerUserId = "google-123"
                every { mockUserRepo.save(any()) } throws DataIntegrityViolationException("모종의 이유로 저장 실패")
                every {
                    mockSocialRepo.findByProviderAndProviderUserId(
                        provider = provider,
                        providerUserId = providerUserId,
                    )
                } returns null

                shouldThrow<DataIntegrityViolationException> {
                    userApi.createSocialUser(provider, providerUserId, "test@email.com")
                }
            }
        }
    })
