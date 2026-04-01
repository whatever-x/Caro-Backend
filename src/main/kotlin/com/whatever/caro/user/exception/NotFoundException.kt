package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class NotFoundException(
    message: String,
) : BusinessException(UserErrorCode.NOT_FOUND, message = message)
