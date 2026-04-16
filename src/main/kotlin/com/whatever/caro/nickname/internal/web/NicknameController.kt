package com.whatever.caro.nickname.internal.web

import com.whatever.caro.common.response.ApiResponse
import com.whatever.caro.nickname.NicknameApi
import com.whatever.caro.nickname.internal.web.response.NicknameResponse
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/nicknames")
class NicknameController(
    private val nicknameService: NicknameApi,
) {

    @GetMapping("/random")
    fun getRandomNickname(): ResponseEntity<ApiResponse<NicknameResponse>> {
        val locale = LocaleContextHolder.getLocale()
        val nickname = nicknameService.randomName(locale)

        return ResponseEntity.ok(ApiResponse.ok(NicknameResponse(nickname)))
    }
}
