package com.whatever.caro.auth.exception

import com.whatever.caro.common.exception.BusinessException

class InvalidRefreshTokenException(
    message: String = AuthErrorCode.INVALID_REFRESH_TOKEN.message,
    cause: Throwable? = null,
) : BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN, message = message, cause = cause)
