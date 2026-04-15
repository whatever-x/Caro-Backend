package com.whatever.caro.nickname

import java.util.Locale

/**
 * Docker 스타일의 랜덤 닉네임 생성기.
 *
 * 사용 예시:
 *   nicknameApi.randomName()                          // "brave_newton"
 *   nicknameApi.randomName(Locale.KOREAN)              // "용감한_뉴턴"
 *   nicknameApi.create().adjective().person().get()     // "happy_turing"
 *   nicknameApi.create().adjective().anyNoun().get()    // "calm_fox" 또는 "calm_pikachu"
 */
interface NicknameApi {
    /** 닉네임을 조합할 수 있는 빌더를 반환한다. */
    fun create(
        locale: Locale = NicknameLocales.DEFAULT,
    ): NicknameBuilder

    /** 형용사 + 명사 계열 단어로 구성된 랜덤 닉네임을 반환한다. */
    fun randomName(
        locale: Locale = NicknameLocales.DEFAULT,
        separator: String = "_",
    ): String
}
