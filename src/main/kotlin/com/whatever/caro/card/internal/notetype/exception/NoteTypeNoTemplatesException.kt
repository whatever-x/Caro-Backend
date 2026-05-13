package com.whatever.caro.card.internal.notetype.exception

import com.whatever.caro.common.exception.BusinessException

class NoteTypeNoTemplatesException(
    message: String,
) : BusinessException(errorCode = NoteTypeErrorCode.NO_TEMPLATES, message = message)
