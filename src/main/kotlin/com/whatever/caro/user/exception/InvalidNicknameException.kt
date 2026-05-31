package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class InvalidNicknameException(
    message: String,
) : BusinessException(UserErrorCode.INVALID_NICKNAME, message = message)
