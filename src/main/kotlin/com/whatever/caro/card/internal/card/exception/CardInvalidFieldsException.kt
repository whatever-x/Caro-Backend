package com.whatever.caro.card.internal.card.exception

import com.whatever.caro.common.exception.BusinessException

class CardInvalidFieldsException(
    message: String,
) : BusinessException(errorCode = CardErrorCode.INVALID_FIELDS, message = message)
