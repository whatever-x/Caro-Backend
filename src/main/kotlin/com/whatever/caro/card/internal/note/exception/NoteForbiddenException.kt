package com.whatever.caro.card.internal.note.exception

import com.whatever.caro.common.exception.BusinessException

class NoteForbiddenException(
    message: String,
) : BusinessException(errorCode = NoteErrorCode.FORBIDDEN, message = message)
