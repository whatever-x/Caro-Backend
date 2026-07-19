package com.whatever.caro.auth.exception

import com.whatever.caro.common.exception.BusinessException

class WithdrawnException(
    message: String = AuthErrorCode.WITHDRAWN.message,
    cause: Throwable? = null,
) : BusinessException(AuthErrorCode.WITHDRAWN, message = message, cause = cause)
