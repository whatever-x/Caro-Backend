package com.whatever.caro.banner.internal.web

import com.whatever.caro.banner.internal.BannerPlatform
import com.whatever.caro.banner.internal.HomeBannerService
import com.whatever.caro.banner.internal.web.response.HomeBannersResponse
import com.whatever.caro.common.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Home banners", description = "서버 설정으로 운영하는 홈 이미지 배너")
@RestController
@RequestMapping("/home/banners")
class HomeBannerController(
    private val service: HomeBannerService,
) {
    @Operation(summary = "홈 이미지 배너 목록", description = "ACTIVE 사용자에게 현재 유효한 소재를 최대 5개 반환합니다.")
    @GetMapping(version = "1.0")
    fun list(
        @RequestParam platform: BannerPlatform,
        @RequestParam appVersion: String,
    ): ResponseEntity<ApiResponse<HomeBannersResponse>> =
        ResponseEntity.ok().cacheControl(
            CacheControl.noStore(),
        ).body(ApiResponse.ok(service.list(platform, appVersion)))
}
