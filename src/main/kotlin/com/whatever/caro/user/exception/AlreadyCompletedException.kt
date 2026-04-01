package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class AlreadyCompletedException(
    message: String,
) : BusinessException(UserErrorCode.ALREADY_COMPLETED, message = message)
