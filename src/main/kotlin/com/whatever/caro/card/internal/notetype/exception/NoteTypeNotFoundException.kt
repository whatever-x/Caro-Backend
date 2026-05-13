package com.whatever.caro.card.internal.notetype.exception

import com.whatever.caro.common.exception.BusinessException

class NoteTypeNotFoundException(
    message: String,
) : BusinessException(errorCode = NoteTypeErrorCode.NOT_FOUND, message = message)
