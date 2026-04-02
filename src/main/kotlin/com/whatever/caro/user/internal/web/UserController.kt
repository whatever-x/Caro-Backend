package com.whatever.caro.user.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.internal.web.response.NicknameCheckResponse
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Validated
@RestController
@RequestMapping("/v1/users")
class UserController(
    private val userApi: UserApi,
) {

    @GetMapping("/nicknames/{nickname}/availability")
    fun checkNicknameAvailability(
        @PathVariable
        @NotBlank(message = "Nickname is required")
        @Size(max = 50, message = "Nickname is required")
        nickname: String,
    ): ResponseEntity<ApiResponse<NicknameCheckResponse>> {
        val available = userApi.isNicknameAvailable(nickname)
        return ResponseEntity.ok(ApiResponse.ok(NicknameCheckResponse(nickname, available)))
    }
}
