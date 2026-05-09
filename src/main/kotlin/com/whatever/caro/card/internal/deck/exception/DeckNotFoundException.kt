package com.whatever.caro.card.internal.deck.exception

import com.whatever.caro.common.exception.BusinessException

class DeckNotFoundException(
    message: String,
) : BusinessException(errorCode = DeckErrorCode.NOT_FOUND, message = message)
