package com.whatever.caro.auth

data class AuthUser(
    val userId: Long,
    val jti: String,
    val status: String,
)
