package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class InvalidBirthDateException(
    message: String,
) : BusinessException(UserErrorCode.INVALID_BIRTH_DATE, message = message)
