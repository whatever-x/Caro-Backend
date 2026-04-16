package com.whatever.caro.auth.exception

import com.whatever.caro.common.exception.BusinessException

class InvalidSocialTokenException(
    message: String = AuthErrorCode.SOCIAL_FAILED.message,
    cause: Throwable? = null,
) : BusinessException(AuthErrorCode.SOCIAL_FAILED, message = message, cause = cause)
