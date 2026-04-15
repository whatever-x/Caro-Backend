package com.whatever.caro.user.internal

import com.whatever.caro.TestcontainersConfiguration
import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.AlreadyCompletedException
import com.whatever.caro.user.exception.NicknameDuplicatedException
import com.whatever.caro.user.exception.UserNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.modulith.test.ApplicationModuleTest
import java.time.Instant
import java.util.UUID

@ApplicationModuleTest(extraIncludes = ["common"])
@Import(TestcontainersConfiguration::class)
class UserServiceTest(
    private val userService: UserService,
    private val userRepository: UserRepository,
    private val socialAccountRepository: SocialAccountRepository,
) : DescribeSpec({

    afterEach {
        socialAccountRepository.deleteAllInBatch()
        userRepository.deleteAllInBatch()
    }

    fun createSuspendedUser(
        nickname: String = "temp_abcd1234",
    ): User = userRepository.save(User(nickname = nickname))

    fun createActiveUser(
        nickname: String = "active_user",
    ): User = userRepository.save(User(nickname = nickname, status = UserStatus.ACTIVE, isTermsAgreed = true))

    fun createSocialUser(
        user: User,
        provider: SocialProvider,
        providerUserId: String,
        email: String? = null,
    ): SocialAccount = socialAccountRepository.save(SocialAccount(user = user, provider = provider, providerUserId = providerUserId, email = email))

    describe("createSocialUser") {
        data class EmailCase(
            val email: String?,
            val reason: String,
        )

        withData(
            nameFn = { "소셜 회원가입 성공: ${it.reason}" },
            EmailCase("test@email.com", "이메일이 존재하는 경우"),
            EmailCase(null, "이메일이 없는 경우"),
        ) { (email, _) ->
            val result = userService.createSocialUser(
                provider = SocialProvider.GOOGLE,
                providerUserId = "google-${UUID.randomUUID().toString().take(8)}}",
                email = email,
            )

            val createdUser = userRepository.findByIdOrNull(result.id)
            result.status shouldBe UserStatus.SUSPENDED
            result.isTermsAgreed shouldBe false
            result.id shouldBe createdUser!!.id
        }
    }

    describe("completeRegistration") {
        it("정상적으로 등록을 완료한다") {
            val user = createSuspendedUser()
            user.status shouldBe UserStatus.SUSPENDED

            val result = userService.completeRegistration(
                userId = user.id,
                nickname = "NewNick",
                isTermsAgreed = true,
            )

            result.status shouldBe UserStatus.ACTIVE
            result.isTermsAgreed shouldBe true
            result.nickname shouldBe "NewNick"
        }

        it("가입하지 않은 userId로 가입을 완료한다면 예외를 던진다") {
            val invalidUserId = 0L

            userRepository.findByIdOrNull(invalidUserId) shouldBe null

            shouldThrow<UserNotFoundException> {
                userService.completeRegistration(
                    userId = invalidUserId,
                    nickname = "Nick",
                    isTermsAgreed = true,
                )
            }
        }

        it("이미 ACTIVE인 사용자는 예외를 던진다") {
            val user = createActiveUser()
            user.status shouldBe UserStatus.ACTIVE

            shouldThrow<AlreadyCompletedException> {
                userService.completeRegistration(
                    userId = user.id,
                    nickname = "Nick",
                    isTermsAgreed = true,
                )
            }
        }

        it("닉네임이 중복이면 예외를 던진다") {
            val existingNickname = "existing-nickname"
            val user1 = createActiveUser(nickname = existingNickname)

            val user2 = createSuspendedUser()
            shouldThrow<NicknameDuplicatedException> {
                userService.completeRegistration(
                    userId = user2.id,
                    nickname = existingNickname,
                    isTermsAgreed = true,
                )
            }
        }

        it("유효하지 않은 닉네임 형식은 예외를 던진다") {
            val user = createSuspendedUser()
            val invalidNickname = "??invalid-nick@#!"

            shouldThrow<NicknameDuplicatedException> {
                userService.completeRegistration(
                    userId = user.id,
                    nickname = invalidNickname,
                    isTermsAgreed = true,
                )
            }
        }

        it("약관 미동의 시 IllegalArgumentException을 던진다") {
            shouldThrow<IllegalArgumentException> {
                userService.completeRegistration(
                    userId = 1L,
                    nickname = "Nick",
                    isTermsAgreed = false,
                )
            }
        }
    }

    describe("findById") {
        it("존재하는 유저를 반환한다") {
            val user = createSuspendedUser("TestUser")

            val result = userService.findById(user.id)

            result.shouldNotBeNull()
            result.id shouldBe user.id
            result.nickname shouldBe "TestUser"
        }

        it("존재하지 않으면 null을 반환한다") {
            userService.findById(0L).shouldBeNull()
        }
    }

    describe("findBySocialProvider") {
        it("존재하는 소셜 계정의 유저를 반환한다") {
            val user = createSuspendedUser()
            val providerUserId = "google-123"
            createSocialUser(
                user,
                provider = SocialProvider.GOOGLE,
                providerUserId = providerUserId,
                email = "test@email.com",
            )

            val result = userService.findBySocialProvider(
                provider = SocialProvider.GOOGLE,
                providerUserId = providerUserId,
            )

            result.shouldNotBeNull()
            result.id shouldBe user.id
        }

        it("존재하지 않으면 null을 반환한다") {
            userService.findBySocialProvider(
                provider = SocialProvider.APPLE,
                providerUserId = "unknown",
            ).shouldBeNull()
        }
    }

    describe("isNicknameAvailable") {
        it("유효하고 미존재하는 닉네임은 true를 반환한다") {
            userService.isNicknameAvailable("valid-name").shouldBeTrue()
        }

        it("이미 존재하는 닉네임은 false를 반환한다") {
            val existingName = "existing-name"
            createActiveUser(existingName)

            userService.isNicknameAvailable(existingName).shouldBeFalse()
        }

        it("soft-delete된 유저의 닉네임은 사용 가능하다") {
            val softDeletedNickname = "DeletedNick"
            val user = createActiveUser(softDeletedNickname)
            user.softDelete(Instant.now())
            userRepository.save(user)

            userService.isNicknameAvailable(softDeletedNickname).shouldBeTrue()
        }

        describe("닉네임 regex 검증") {
            it("빈 문자열은 false를 반환한다") {
                userService.isNicknameAvailable("").shouldBeFalse()
            }

            it("공백(whitespace)만 있는 문자열은 false를 반환한다") {
                userService.isNicknameAvailable("   ").shouldBeFalse()
            }

            it("정확히 50자 닉네임은 유효하다") {
                userService.isNicknameAvailable("a".repeat(50)).shouldBeTrue()
            }

            it("51자 초과 닉네임은 false를 반환한다") {
                userService.isNicknameAvailable("a".repeat(51)).shouldBeFalse()
            }

            it("허용 특수문자(-, _, 공백)는 유효하다") {
                userService.isNicknameAvailable("my-nick_name test").shouldBeTrue()
            }

            it("비허용 특수문자는 false를 반환한다") {
                userService.isNicknameAvailable("nick@name#").shouldBeFalse()
            }

            it("숫자가 포함되면 false를 반환한다") {
                userService.isNicknameAvailable("nick123").shouldBeFalse()
            }

            it("한글 닉네임은 유효하다") {
                userService.isNicknameAvailable("멋진닉네임").shouldBeTrue()
            }
        }
    }
})
