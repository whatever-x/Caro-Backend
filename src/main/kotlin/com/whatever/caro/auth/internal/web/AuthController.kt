package com.whatever.caro.auth.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.auth.internal.AuthService
import com.whatever.caro.auth.internal.web.request.CompleteRegistrationRequest
import com.whatever.caro.auth.internal.web.request.RefreshTokenRequest
import com.whatever.caro.auth.internal.web.request.SocialLoginRequest
import com.whatever.caro.auth.internal.web.response.SocialLoginResponse
import com.whatever.caro.auth.internal.web.response.TokenResponse
import com.whatever.caro.common.openapi.PublicApi
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "인증 / 세션 관리")
@RestController
@RequestMapping("/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @Operation(
        summary = "소셜 로그인",
        description = "소셜 ID 토큰을 검증해 access/refresh 토큰을 발급한다. 신규 사용자는 isRegistrationComplete=false 로 응답.",
    )
    @PublicApi
    @PostMapping("/social-login")
    fun socialLogin(
        @Parameter(name = "Device-Id", description = "디바이스 식별자", required = true, example = "unique-device-identifier")
        @RequestHeader(name = "Device-Id", required = true) deviceId: String,
        @Valid @RequestBody request: SocialLoginRequest,
    ): ResponseEntity<ApiResponse<SocialLoginResponse>> {
        val response = authService.socialLogin(request, deviceId)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @Operation(
        summary = "회원가입 완료",
        description = "닉네임/약관 동의 입력 후 access/refresh 토큰을 재발급한다.",
    )
    @PostMapping("/complete-registration")
    fun completeRegistration(
        @Parameter(name = "Device-Id", description = "디바이스 식별자", required = true, example = "unique-device-identifier")
        @RequestHeader(name = "Device-Id", required = true) deviceId: String,
        @Valid @RequestBody request: CompleteRegistrationRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val authUser = SecurityUtil.currentUser()
        val response = authService.completeRegistration(authUser, request, deviceId)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @Operation(
        summary = "토큰 재발급",
        description = "Refresh Token 으로 access/refresh 토큰을 재발급한다. 단일 사용 정책.",
    )
    @PublicApi
    @PostMapping("/refresh")
    fun refreshToken(
        @Parameter(name = "Device-Id", description = "디바이스 식별자", required = true, example = "unique-device-identifier")
        @RequestHeader(name = "Device-Id", required = true) deviceId: String,
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<TokenResponse>> {
        val response = authService.reissueToken(request, deviceId)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @Operation(
        summary = "로그아웃",
        description = "현재 access token 을 블랙리스트 처리하고 디바이스 refresh token 을 폐기한다.",
    )
    @PostMapping("/logout")
    fun logout(
        @Parameter(name = "Device-Id", description = "디바이스 식별자", required = true, example = "unique-device-identifier")
        @RequestHeader(name = "Device-Id", required = true) deviceId: String,
    ): ResponseEntity<ApiResponse<Unit>> {
        val authUser = SecurityUtil.currentUser()
        authService.logout(authUser, deviceId)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }

    @Operation(
        summary = "탈퇴",
        description = "유저를 탈퇴처리 하고, 현재 access token 을 블랙리스트 처리하고 유저의 모든 refresh token 을 폐기한다.",
    )
    @DeleteMapping("/withdraw")
    fun withdraw(): ResponseEntity<ApiResponse<Unit>> {
        val authUser = SecurityUtil.currentUser()
        authService.withdrawUser(authUser)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }
}
