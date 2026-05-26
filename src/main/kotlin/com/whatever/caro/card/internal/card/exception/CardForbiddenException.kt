package com.whatever.caro.card.internal.card.exception

import com.whatever.caro.common.exception.BusinessException

class CardForbiddenException(
    message: String,
) : BusinessException(errorCode = CardErrorCode.FORBIDDEN, message = message)
