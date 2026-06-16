package com.whatever.caro.card.internal.deck.exception

import com.whatever.caro.common.exception.BusinessException

class DeckPresetNotFoundException(
    message: String,
) : BusinessException(errorCode = DeckErrorCode.PRESET_NOT_FOUND, message = message)
