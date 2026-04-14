package com.whatever.caro.auth.internal.web

import com.whatever.caro.auth.SecurityUtil
import com.whatever.caro.auth.internal.AuthService
import com.whatever.caro.auth.internal.web.request.CompleteRegistrationRequest
import com.whatever.caro.auth.internal.web.request.LogoutRequest
import com.whatever.caro.auth.internal.web.request.RefreshTokenRequest
import com.whatever.caro.auth.internal.web.request.SocialLoginRequest
import com.whatever.caro.common.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
) {
    @PostMapping("/social-login")
    fun socialLogin(
        @Valid @RequestBody request: SocialLoginRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val response = authService.socialLogin(request)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/complete-registration")
    fun completeRegistration(
        @Valid @RequestBody request: CompleteRegistrationRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val authUser = SecurityUtil.currentUser()
        val response = authService.completeRegistration(authUser, request)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val response = authService.reissueToken(request)
        return ResponseEntity.ok(ApiResponse.ok(response))
    }

    @PostMapping("/logout")
    fun logout(
        @Valid @RequestBody request: LogoutRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        val authUser = SecurityUtil.currentUser()
        authService.logout(authUser, request.deviceId)
        return ResponseEntity.ok(ApiResponse.ok(Unit))
    }
}
