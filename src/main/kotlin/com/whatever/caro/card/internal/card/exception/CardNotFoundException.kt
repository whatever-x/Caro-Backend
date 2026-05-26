package com.whatever.caro.card.internal.card.exception

import com.whatever.caro.common.exception.BusinessException

class CardNotFoundException(
    message: String,
) : BusinessException(errorCode = CardErrorCode.NOT_FOUND, message = message)
