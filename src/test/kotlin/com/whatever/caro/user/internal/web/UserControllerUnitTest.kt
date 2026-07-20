package com.whatever.caro.user.internal.web

import com.whatever.caro.user.UserApi
import com.whatever.caro.user.UserInfo
import com.whatever.caro.user.UserStatus
import com.whatever.caro.user.exception.UserNotFoundException
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

        describe("getMyNickname") {
            it("유저가 존재하면 200과 닉네임을 반환한다") {
                val userId = 1L
                every { userApi.findById(userId) } returns UserInfo(
                    id = userId,
                    nickname = "다정한 고슴도치",
                    status = UserStatus.ACTIVE,
                    isTermsAgreed = true,
                    isDeleted = false,
                )

                val response = controller.getMyNickname(userId)

                response.statusCode shouldBe HttpStatus.OK
                response.body!!.success shouldBe true
                response.body!!.data!!.nickname shouldBe "다정한 고슴도치"
            }

            it("유저가 존재하지 않으면 UserNotFoundException을 던진다") {
                val userId = 999L
                every { userApi.findById(userId) } returns null

                shouldThrow<UserNotFoundException> {
                    controller.getMyNickname(userId)
                }
            }
        }
    })
