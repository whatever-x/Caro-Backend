package com.whatever.caro.auth.exception

import com.whatever.caro.common.exception.BusinessException

class InvalidAccessTokenException(
    message: String = AuthErrorCode.INVALID_TOKEN.message,
    cause: Throwable? = null,
) : BusinessException(AuthErrorCode.INVALID_TOKEN, message = message, cause = cause)
