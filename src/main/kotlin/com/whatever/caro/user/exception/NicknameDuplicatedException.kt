package com.whatever.caro.user.exception

import com.whatever.caro.common.exception.BusinessException

class NicknameDuplicatedException(
    message: String,
) : BusinessException(UserErrorCode.NICKNAME_DUPLICATED, message = message)
