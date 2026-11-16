package com.whatever.caro.card.internal.card.exception

import com.whatever.caro.common.exception.BusinessException

class CardLimitExceededException : BusinessException(errorCode = CardErrorCode.LIMIT_EXCEEDED)
