package com.whatever.caro.user.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.user.UserApi
import com.whatever.caro.user.internal.UserService
import com.whatever.caro.user.internal.web.request.UpdateNicknameRequest
import com.whatever.caro.user.internal.web.response.NicknameCheckResponse
import com.whatever.caro.user.internal.web.response.UpdateNicknameResponse
import com.whatever.caro.user.internal.web.response.toUpdateNicknameResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "User", description = "사용자 정보 조회")
@Validated
@RestController
@RequestMapping("/v1/users")
class UserController(
    private val userApi: UserApi,
    private val userService: UserService,
) {

    @Operation(
        summary = "닉네임 사용 가능 여부 조회",
        description = "닉네임 중복 여부를 반환한다. 길이 제한 50자.",
    )
    @GetMapping("/nicknames/{nickname}/availability")
    fun checkNicknameAvailability(
        @PathVariable
        @NotBlank(message = "Nickname is required")
        @Size(min = 1, max = 50, message = "Nickname must be 1-50 characters")
        nickname: String,
    ): ResponseEntity<ApiResponse<NicknameCheckResponse>> {
        val available = userApi.isNicknameAvailable(nickname)
        return ResponseEntity.ok(ApiResponse.ok(NicknameCheckResponse(nickname, available)))
    }

    @Operation(
        summary = "닉네임 변경",
        description = "현재 로그인한 사용자의 닉네임을 변경한다.",
    )
    @PatchMapping("/me/nickname")
    fun updateNickname(
        @Valid @RequestBody request: UpdateNicknameRequest,
        @AuthenticationPrincipal(expression = "userId")
        userId: Long,
    ): ResponseEntity<ApiResponse<UpdateNicknameResponse>> {
        val updated = userService.updateNickname(userId = userId, nickname = request.nickname)
        return ResponseEntity.ok(ApiResponse.ok(updated.toUpdateNicknameResponse()))
    }
}
