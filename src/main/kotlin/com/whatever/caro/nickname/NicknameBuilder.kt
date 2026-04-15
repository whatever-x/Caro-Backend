package com.whatever.caro.nickname

/**
 * 단어 카테고리를 조합하여 닉네임을 생성하는 Fluent API.
 *
 * 각 메서드는 새로운 불변 빌더 인스턴스를 반환한다.
 * 템플릿을 저장해두고 재사용할 수 있으며, [get] 호출마다 새로운 랜덤 닉네임을 생성한다.
 *
 *   val template = nicknameApi.create().adjective().person()
 *   template.get()  // "happy_turing"
 *   template.get()  // "brave_curie"
 */
interface NicknameBuilder {
    fun adjective(): NicknameBuilder
    fun animal(): NicknameBuilder
    fun color(): NicknameBuilder
    fun noun(): NicknameBuilder

    /** noun, animal, person, mythical, legend, food 중 랜덤 카테고리에서 단어를 선택한다. */
    fun anyNoun(): NicknameBuilder
    fun person(): NicknameBuilder
    fun mythical(): NicknameBuilder
    fun legend(): NicknameBuilder
    fun food(): NicknameBuilder
    fun withSeparator(
        separator: String,
    ): NicknameBuilder

    fun get(): String
}
