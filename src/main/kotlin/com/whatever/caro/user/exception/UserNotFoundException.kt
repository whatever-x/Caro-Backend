package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class UserNotFoundException(
    message: String,
) : BusinessException(errorCode = UserErrorCode.NOT_FOUND, message = message)
