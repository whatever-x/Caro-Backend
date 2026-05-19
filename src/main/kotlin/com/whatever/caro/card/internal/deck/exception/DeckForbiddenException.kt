package com.whatever.caro.card.internal.deck.exception

import com.whatever.caro.common.exception.BusinessException

class DeckForbiddenException(
    message: String,
) : BusinessException(errorCode = DeckErrorCode.FORBIDDEN, message = message)
