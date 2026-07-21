package com.whatever.caro.user

data class UserInfo(
    val id: Long,
    val nickname: String,
    val status: UserStatus,
    val isTermsAgreed: Boolean,
    val isDeleted: Boolean,
)
