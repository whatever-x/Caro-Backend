package com.whatever.caro.user.internal.web

import com.whatever.caro.user.SocialProvider
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.exception.UserNotFoundException
import com.whatever.caro.user.internal.MyInfo
import com.whatever.caro.user.internal.UserService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.http.HttpStatus

class UserControllerUnitTest :
    DescribeSpec({

        val userApi = mockk<UserApi>()
        val userService = mockk<UserService>()
        val controller = UserController(userApi, userService)

        describe("getUserInfo") {
            it("유저가 존재하면 200과 내 정보를 반환한다") {
                val userId = 1L
                every { userService.getUserInfo(userId) } returns MyInfo(
                    nickname = "다정한 고슴도치",
                    email = "user@example.com",
                    socialProvider = SocialProvider.GOOGLE,
                )

                val response = controller.getMyInfo(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.nickname shouldBe "다정한 고슴도치"
                response.body!!.data!!.email shouldBe "user@example.com"
                response.body!!.data!!.loginPlatform shouldBe SocialProvider.GOOGLE
            }

            it("소셜 제공자가 이메일을 주지 않은 유저는 email이 null인 응답을 반환한다") {
                val userId = 2L
                every { userService.getUserInfo(userId) } returns MyInfo(
                    nickname = "조용한 부엉이",
                    email = null,
                    socialProvider = SocialProvider.APPLE,
                )

                val response = controller.getMyInfo(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.data!!.email shouldBe null
                response.body!!.data!!.loginPlatform shouldBe SocialProvider.APPLE
            }

            it("유저가 존재하지 않으면 UserNotFoundException을 던진다") {
                val userId = 999L
                every {
                    userService.getUserInfo(userId)
                } throws UserNotFoundException("사용자를 찾을 수 없습니다: $userId")

                shouldThrow<UserNotFoundException> {
                    controller.getMyInfo(userId)
                }
            }
        }
    })
