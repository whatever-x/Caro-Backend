package com.whatever.caro.user.internal

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.internal.encrypt.EmailHasher
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.hibernate.exception.ConstraintViolationException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.PlatformTransactionManager
import java.sql.SQLException
import java.time.Clock

class UserServiceUnitTest :
    DescribeSpec({

        val mockEmailHasher = mockk<EmailHasher>(relaxed = true)
        val mockUserRepo = mockk<UserRepository>(relaxed = true)
        val mockSocialRepo = mockk<SocialAccountRepository>(relaxed = true)
        val userApi: UserApi = UserService(clock = Clock.systemUTC(), emailHasher = mockEmailHasher, userRepository = mockUserRepo, socialAccountRepository = mockSocialRepo, transactionManager = mockk<PlatformTransactionManager>(relaxed = true))

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

                every { mockUserRepo.save(any()) } throws DataIntegrityViolationException("provider conflict", ConstraintViolationException("duplicate", SQLException("duplicate", "23000", 1062), "uk_provider_user"))
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
                every { mockUserRepo.save(any()) } throws DataIntegrityViolationException("provider conflict", ConstraintViolationException("duplicate", SQLException("duplicate", "23000", 1062), "uk_provider_user"))
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
