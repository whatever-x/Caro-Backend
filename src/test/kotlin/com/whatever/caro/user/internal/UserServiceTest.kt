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
    ): SocialAccount = socialAccountRepository.save(SocialAccount(user = user, provider = provider, providerUserId = providerUserId, encryptedEmail = email))

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

    describe("updateNickname") {
        it("정상적으로 닉네임을 변경한다") {
            val user = createActiveUser(nickname = "oldNick")

            val result = userService.updateNickname(
                userId = user.id,
                nickname = "newNick",
            )

            result.id shouldBe user.id
            result.nickname shouldBe "newNick"
            userRepository.findByIdOrNull(user.id)!!.nickname shouldBe "newNick"
        }

        it("존재하지 않는 userId이면 UserNotFoundException을 던진다") {
            val invalidUserId = 0L
            userRepository.findByIdOrNull(invalidUserId) shouldBe null

            shouldThrow<UserNotFoundException> {
                userService.updateNickname(
                    userId = invalidUserId,
                    nickname = "newNick",
                )
            }
        }

        it("이미 다른 유저가 사용 중인 닉네임이면 NicknameDuplicatedException을 던진다") {
            val takenNickname = "takenNick"
            createActiveUser(nickname = takenNickname)
            val target = createActiveUser(nickname = "myNick")

            shouldThrow<NicknameDuplicatedException> {
                userService.updateNickname(
                    userId = target.id,
                    nickname = takenNickname,
                )
            }
        }

        it("유효하지 않은 닉네임 형식이면 예외를 던진다") {
            val user = createActiveUser(nickname = "myNick")

            shouldThrow<NicknameDuplicatedException> {
                userService.updateNickname(
                    userId = user.id,
                    nickname = "??invalid@#",
                )
            }
        }

        it("현재 닉네임과 동일하면 변경 없이 현재 정보를 반환한다") {
            val sameNickname = "sameNick"
            val user = createActiveUser(nickname = sameNickname)

            val result = userService.updateNickname(
                userId = user.id,
                nickname = sameNickname,
            )

            result.nickname shouldBe sameNickname
            userRepository.findByIdOrNull(user.id)!!.nickname shouldBe sameNickname
        }

        it("다른 유저가 soft-delete된 닉네임은 사용 가능하다") {
            val recycledNickname = "recycledNick"
            val deletedUser = createActiveUser(nickname = recycledNickname)
            deletedUser.softDelete(Instant.now())
            userRepository.save(deletedUser)

            val target = createActiveUser(nickname = "myNick")
            val result = userService.updateNickname(
                userId = target.id,
                nickname = recycledNickname,
            )

            result.nickname shouldBe recycledNickname
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
            context("길이 검증") {
                it("빈 문자열은 false를 반환한다") {
                    userService.isNicknameAvailable("").shouldBeFalse()
                }

                it("공백(whitespace)만 있는 문자열은 false를 반환한다") {
                    userService.isNicknameAvailable("   ").shouldBeFalse()
                }

                it("1자 닉네임은 유효하지 않다") {
                    userService.isNicknameAvailable("a").shouldBeFalse()
                }

                it("최소 길이인 2자 닉네임은 유효하다") {
                    userService.isNicknameAvailable("ab").shouldBeTrue()
                    userService.isNicknameAvailable("가나").shouldBeTrue()
                }

                it("최대 길이인 20자 닉네임은 유효하다") {
                    userService.isNicknameAvailable("a".repeat(20)).shouldBeTrue()
                }

                it("21자 초과 닉네임은 유효하지 않다") {
                    userService.isNicknameAvailable("a".repeat(21)).shouldBeFalse()
                }
            }

            context("문자 및 기호 검증") {
                it("한글, 영문 닉네임은 유효하다") {
                    userService.isNicknameAvailable("멋진닉네임").shouldBeTrue()
                    userService.isNicknameAvailable("AwesomeName").shouldBeTrue()
                }

                it("숫자가 포함된 닉네임은 유효하다") {
                    userService.isNicknameAvailable("nick123").shouldBeTrue()
                }

                it("허용된 특수문자(-, _)가 중간에 포함되면 유효하다") {
                    userService.isNicknameAvailable("my-nick_name").shouldBeTrue()
                }

                it("비허용 특수문자가 포함되면 false를 반환한다") {
                    userService.isNicknameAvailable("nick@name#").shouldBeFalse()
                    userService.isNicknameAvailable("nick name").shouldBeFalse() // 공백 포함
                }

                it("완성형이 아닌 한글 자음/모음 단독 사용은 false를 반환한다") {
                    userService.isNicknameAvailable("ㅋㅋㅋ").shouldBeFalse()
                    userService.isNicknameAvailable("유저ㅠㅠ").shouldBeFalse()
                }

                it("허용된 특수문자(-, _)가 연속으로 포함되면 false를 반환한다.") {
                    userService.isNicknameAvailable("nick--name").shouldBeFalse()
                    userService.isNicknameAvailable("nick__name").shouldBeFalse()
                    userService.isNicknameAvailable("nick_-name").shouldBeFalse()
                }
            }

            context("구분자(-, _) 위치 검증") {
                it("구분자로 시작하는 닉네임은 false를 반환한다") {
                    userService.isNicknameAvailable("-username").shouldBeFalse()
                    userService.isNicknameAvailable("_username").shouldBeFalse()
                }

                it("구분자로 끝나는 닉네임은 false를 반환한다") {
                    userService.isNicknameAvailable("username-").shouldBeFalse()
                    userService.isNicknameAvailable("username_").shouldBeFalse()
                }

                it("구분자로만 이루어진 닉네임은 false를 반환한다") {
                    userService.isNicknameAvailable("-_-").shouldBeFalse()
                    userService.isNicknameAvailable("___").shouldBeFalse()
                }
            }

            context("유니코드 및 비표준 문자 검증") {
                it("Zero-width 문자가 포함되면 false를 반환한다") {
                    userService.isNicknameAvailable("nick\u200Bname").shouldBeFalse()
                    userService.isNicknameAvailable("nick\u200Dname").shouldBeFalse()
                    userService.isNicknameAvailable("\uFEFFnickname").shouldBeFalse()
                }

                it("Cyrillic 유사문자가 포함되면 false를 반환한다") {
                    userService.isNicknameAvailable("niсk").shouldBeFalse() // с = Cyrillic U+0441
                    userService.isNicknameAvailable("аdmin").shouldBeFalse() // а = Cyrillic U+0430
                }

                it("이모지가 포함되면 false를 반환한다") {
                    userService.isNicknameAvailable("nick🚀").shouldBeFalse()
                    userService.isNicknameAvailable("😀닉네임").shouldBeFalse()
                }

                it("일본어/중국어 문자가 포함되면 false를 반환한다") {
                    userService.isNicknameAvailable("勇敢な").shouldBeFalse()
                    userService.isNicknameAvailable("ニック").shouldBeFalse()
                }
            }

            context("앞뒤 공백 검증") {
                it("앞뒤 공백이 포함된 닉네임은 false를 반환한다") {
                    userService.isNicknameAvailable(" nickname").shouldBeFalse()
                    userService.isNicknameAvailable("nickname ").shouldBeFalse()
                    userService.isNicknameAvailable(" nickname ").shouldBeFalse()
                }
            }
        }
    }
})
