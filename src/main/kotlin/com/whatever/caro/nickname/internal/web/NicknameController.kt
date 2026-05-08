package com.whatever.caro.nickname.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.nickname.NicknameApi
import com.whatever.caro.nickname.internal.web.response.NicknameResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Nickname", description = "닉네임 추천")
@RestController
@RequestMapping("/v1/nicknames")
class NicknameController(
    private val nicknameService: NicknameApi,
) {

    @Operation(
        summary = "랜덤 닉네임 1개 발급",
        description = "Accept-Language 헤더 로케일에 맞춘 랜덤 닉네임을 반환한다.",
    )
    @GetMapping("/random")
    fun getRandomNickname(): ResponseEntity<ApiResponse<NicknameResponse>> {
        val locale = LocaleContextHolder.getLocale()
        val nickname = nicknameService.randomName(locale)

        return ResponseEntity.ok(ApiResponse.ok(NicknameResponse(nickname)))
    }
}
